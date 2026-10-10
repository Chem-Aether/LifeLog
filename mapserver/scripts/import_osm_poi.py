#!/usr/bin/env python3
import argparse
import json
import math
import os
import struct
import shutil
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

import osmium
from shapely import to_wkb
from shapely.geometry import Point

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from app.settings import POI_PATH

OSM_SOURCE_DIR = Path(__file__).resolve().parent.parent / "data" / "source" / "osm"


TOURISM = {
    "attraction": "attraction", "museum": "museum", "gallery": "museum",
    "zoo": "entertainment", "aquarium": "entertainment", "theme_park": "entertainment",
    "viewpoint": "landmark", "artwork": "landmark", "picnic_site": "park",
}
HISTORIC = {
    "monument", "memorial", "castle", "ruins", "archaeological_site", "city_gate",
    "fort", "tomb", "battlefield", "manor", "palace", "wayside_shrine",
}
LEISURE = {"park", "garden", "nature_reserve", "water_park"}
NATURAL = {"peak", "waterfall", "beach", "cave_entrance", "hot_spring", "volcano", "geyser"}
AMENITY = {"place_of_worship", "theatre", "arts_centre", "planetarium"}
MAN_MADE = {"tower", "lighthouse", "observatory", "obelisk"}


def classify(tags):
    tourism = tags.get("tourism")
    if tourism in TOURISM:
        return TOURISM[tourism], tourism
    historic = tags.get("historic")
    if historic in HISTORIC:
        return "heritage", historic
    leisure = tags.get("leisure")
    if leisure in LEISURE:
        return "park", leisure
    natural = tags.get("natural")
    if natural in NATURAL:
        return "nature", natural
    amenity = tags.get("amenity")
    if amenity in AMENITY:
        return "religious" if amenity == "place_of_worship" else "culture", amenity
    man_made = tags.get("man_made")
    if man_made in MAN_MADE:
        return "landmark", man_made
    if tags.get("boundary") == "national_park":
        return "park", "national_park"
    return None


def polygon_centroid(points):
    if not points:
        return None
    if len(points) < 3:
        return sum(x for x, _ in points) / len(points), sum(y for _, y in points) / len(points)
    twice_area = 0.0
    x_sum = 0.0
    y_sum = 0.0
    for index, current in enumerate(points):
        following = points[(index + 1) % len(points)]
        cross = current[0] * following[1] - following[0] * current[1]
        twice_area += cross
        x_sum += (current[0] + following[0]) * cross
        y_sum += (current[1] + following[1]) * cross
    if abs(twice_area) < 1e-12:
        return sum(x for x, _ in points) / len(points), sum(y for _, y in points) / len(points)
    return x_sum / (3 * twice_area), y_sum / (3 * twice_area)


def display_name(tags):
    return tags.get("name:zh-Hans") or tags.get("name:zh") or tags.get("name")


def address(tags):
    full = tags.get("addr:full")
    if full:
        return full
    return "".join(filter(None, [
        tags.get("addr:province"), tags.get("addr:city"), tags.get("addr:district"),
        tags.get("addr:street"), tags.get("addr:housenumber"),
    ])) or None


def importance(tags, category, is_area):
    score = 10
    if tags.get("wikidata") or tags.get("wikipedia"):
        score += 30
    if tags.get("heritage") or tags.get("heritage:operator"):
        score += 20
    if category in {"attraction", "museum", "heritage", "entertainment"}:
        score += 15
    if tags.get("name:zh") or tags.get("name:zh-Hans"):
        score += 5
    if tags.get("website") or tags.get("contact:website"):
        score += 5
    if is_area:
        score += 3
    return score


class PoiHandler(osmium.SimpleHandler):
    def __init__(self, connection, batch_size=2000):
        super().__init__()
        self.connection = connection
        self.batch_size = batch_size
        self.pending = []
        self.seen = 0
        self.inserted = 0
        self.statement = """
            INSERT OR REPLACE INTO poi (
                source, source_type, source_id, name, name_zh, aliases, category, subcategory,
                longitude, latitude, geom, address, website, phone, opening_hours, wikidata, wikipedia,
                importance, tags_json, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

    def node(self, node):
        if not node.location.valid():
            return
        self._collect("node", node.id, dict(node.tags), node.location.lon, node.location.lat, False)

    def area(self, area):
        points = []
        try:
            for ring in area.outer_rings():
                candidate = [(node.lon, node.lat) for node in ring if node.location.valid()]
                if len(candidate) > len(points):
                    points = candidate
        except (RuntimeError, osmium.InvalidLocationError):
            return
        center = polygon_centroid(points)
        if not center:
            return
        source_type = "way" if area.from_way() else "relation"
        self._collect(source_type, area.orig_id(), dict(area.tags), center[0], center[1], True)

    def _collect(self, source_type, source_id, tags, longitude, latitude, is_area):
        self.seen += 1
        classification = classify(tags)
        name = display_name(tags)
        if not classification or not name or not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
            return
        category, subcategory = classification
        aliases = ";".join(dict.fromkeys(filter(None, [
            tags.get("alt_name"), tags.get("short_name"), tags.get("old_name"), tags.get("name:en"),
        ]))) or None
        useful_tags = {key: value for key, value in tags.items() if key in {
            "operator", "religion", "denomination", "heritage", "stars", "fee", "wheelchair",
        }}
        self.pending.append((
            "osm", source_type, str(source_id), name, tags.get("name:zh-Hans") or tags.get("name:zh"),
            aliases, category, subcategory, longitude, latitude, gpkg_point(longitude, latitude), address(tags),
            tags.get("website") or tags.get("contact:website"),
            tags.get("phone") or tags.get("contact:phone"), tags.get("opening_hours"),
            tags.get("wikidata"), tags.get("wikipedia"), importance(tags, category, is_area),
            json.dumps(useful_tags, ensure_ascii=False, separators=(",", ":")) if useful_tags else None,
            datetime.now(timezone.utc).isoformat(),
        ))
        if len(self.pending) >= self.batch_size:
            self.flush()

    def flush(self):
        if not self.pending:
            return
        self.connection.executemany(self.statement, self.pending)
        self.inserted += len(self.pending)
        self.pending.clear()
        if self.inserted % 10000 < self.batch_size:
            print(f"已写入 {self.inserted:,} 条候选 POI", flush=True)


def gpkg_point(longitude, latitude):
    return b"GP\x00\x01" + struct.pack("<i", 4326) + to_wkb(Point(longitude, latitude), byte_order=1, output_dimension=2)


def create_schema(connection):
    connection.executescript("""
        PRAGMA application_id = 1196444487;
        PRAGMA user_version = 10300;
        CREATE TABLE IF NOT EXISTS gpkg_spatial_ref_sys (
            srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY,
            organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL,
            definition TEXT NOT NULL, description TEXT
        );
        CREATE TABLE IF NOT EXISTS gpkg_contents (
            table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL,
            identifier TEXT UNIQUE, description TEXT DEFAULT '', last_change DATETIME NOT NULL,
            min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER
        );
        CREATE TABLE IF NOT EXISTS gpkg_geometry_columns (
            table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL,
            srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL,
            PRIMARY KEY (table_name, column_name)
        );
        CREATE TABLE IF NOT EXISTS gpkg_extensions (
            table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL,
            definition TEXT NOT NULL, scope TEXT NOT NULL,
            UNIQUE (table_name, column_name, extension_name)
        );
        INSERT OR IGNORE INTO gpkg_spatial_ref_sys VALUES
            ('Undefined Cartesian',-1,'NONE',-1,'undefined','undefined Cartesian coordinate reference system'),
            ('Undefined Geographic',0,'NONE',0,'undefined','undefined geographic coordinate system'),
            ('WGS 84',4326,'EPSG',4326,'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]','WGS 84 longitude/latitude');
        DROP TRIGGER IF EXISTS poi_fts_insert;
        DROP TRIGGER IF EXISTS poi_fts_update;
        DROP TRIGGER IF EXISTS poi_fts_delete;
        DROP TRIGGER IF EXISTS rtree_poi_geom_insert;
        DROP TRIGGER IF EXISTS rtree_poi_geom_update1;
        DROP TRIGGER IF EXISTS rtree_poi_geom_update2;
        DROP TRIGGER IF EXISTS rtree_poi_geom_delete;
        DROP TABLE IF EXISTS poi_fts;
        DROP TABLE IF EXISTS rtree_poi_geom;
        DROP TABLE IF EXISTS poi;
        CREATE TABLE poi (
            poi_id INTEGER PRIMARY KEY AUTOINCREMENT,
            source TEXT NOT NULL, source_type TEXT NOT NULL, source_id TEXT NOT NULL,
            name TEXT NOT NULL, name_zh TEXT, aliases TEXT, category TEXT NOT NULL,
            subcategory TEXT NOT NULL, longitude REAL NOT NULL, latitude REAL NOT NULL,
            address TEXT, website TEXT, phone TEXT, opening_hours TEXT,
            wikidata TEXT, wikipedia TEXT, importance INTEGER NOT NULL DEFAULT 0,
            tags_json TEXT, updated_at TEXT NOT NULL, geom POINT NOT NULL,
            UNIQUE(source, source_type, source_id)
        );
        CREATE INDEX idx_poi_category ON poi(category, subcategory);
        CREATE INDEX idx_poi_importance ON poi(importance DESC);
        CREATE VIRTUAL TABLE rtree_poi_geom USING rtree(id,minx,maxx,miny,maxy);
    """)


def build_indexes(connection):
    print("正在执行近距离同名去重和构建搜索索引...", flush=True)
    connection.execute("""
        DELETE FROM poi WHERE poi_id IN (
            SELECT poi_id FROM (
                SELECT poi_id, ROW_NUMBER() OVER (
                    PARTITION BY lower(name), category, round(longitude, 3), round(latitude, 3)
                    ORDER BY importance DESC, poi_id
                ) AS duplicate_rank FROM poi
            ) WHERE duplicate_rank > 1
        )
    """)
    connection.executescript("""
        CREATE VIRTUAL TABLE poi_fts USING fts5(name, name_zh, aliases, tokenize='trigram');
        INSERT INTO poi_fts(rowid, name, name_zh, aliases)
        SELECT poi_id, name, coalesce(name_zh, ''), coalesce(aliases, '') FROM poi;
        INSERT INTO rtree_poi_geom SELECT poi_id, longitude, longitude, latitude, latitude FROM poi;
        DELETE FROM gpkg_contents WHERE table_name='poi';
        DELETE FROM gpkg_geometry_columns WHERE table_name='poi';
        DELETE FROM gpkg_extensions WHERE table_name='poi';
    """)
    bounds = connection.execute("SELECT min(longitude),min(latitude),max(longitude),max(latitude) FROM poi").fetchone()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    connection.execute(
        "INSERT INTO gpkg_contents VALUES ('poi','features','POI','OpenStreetMap points',?,?,?,?,?,4326)",
        (now, *bounds),
    )
    connection.execute("INSERT INTO gpkg_geometry_columns VALUES ('poi','geom','POINT',4326,0,0)")
    connection.execute("INSERT INTO gpkg_extensions VALUES ('poi','geom','gpkg_rtree_index','http://www.geopackage.org/spec/#extension_rtree','write-only')")
    connection.executescript("""
        CREATE TRIGGER poi_fts_insert AFTER INSERT ON poi BEGIN
            INSERT INTO poi_fts(rowid,name,name_zh,aliases) VALUES (NEW.poi_id,NEW.name,coalesce(NEW.name_zh,''),coalesce(NEW.aliases,''));
        END;
        CREATE TRIGGER poi_fts_delete AFTER DELETE ON poi BEGIN
            DELETE FROM poi_fts WHERE rowid=OLD.poi_id;
        END;
        CREATE TRIGGER poi_fts_update AFTER UPDATE OF name,name_zh,aliases ON poi BEGIN
            DELETE FROM poi_fts WHERE rowid=OLD.poi_id;
            INSERT INTO poi_fts(rowid,name,name_zh,aliases) VALUES (NEW.poi_id,NEW.name,coalesce(NEW.name_zh,''),coalesce(NEW.aliases,''));
        END;
        CREATE TRIGGER rtree_poi_geom_insert AFTER INSERT ON poi BEGIN
            INSERT OR REPLACE INTO rtree_poi_geom VALUES (NEW.poi_id,ST_MinX(NEW.geom),ST_MaxX(NEW.geom),ST_MinY(NEW.geom),ST_MaxY(NEW.geom));
        END;
        CREATE TRIGGER rtree_poi_geom_update AFTER UPDATE OF geom ON poi BEGIN
            INSERT OR REPLACE INTO rtree_poi_geom VALUES (NEW.poi_id,ST_MinX(NEW.geom),ST_MaxX(NEW.geom),ST_MinY(NEW.geom),ST_MaxY(NEW.geom));
            UPDATE poi SET longitude=ST_X(NEW.geom),latitude=ST_Y(NEW.geom) WHERE poi_id=NEW.poi_id;
        END;
        CREATE TRIGGER rtree_poi_geom_delete AFTER DELETE ON poi BEGIN
            DELETE FROM rtree_poi_geom WHERE id=OLD.poi_id;
        END;
    """)

def main():
    parser = argparse.ArgumentParser(description="从中国 OSM PBF 构建独立 POI GeoPackage")
    parser.add_argument("pbf", type=Path, nargs="?", help="china-*.osm.pbf 路径；省略时自动选择目录中最新文件")
    parser.add_argument("--output", type=Path, default=POI_PATH)
    args = parser.parse_args()
    if args.pbf is None:
        candidates = sorted(OSM_SOURCE_DIR.glob("china-*.osm.pbf"))
        args.pbf = candidates[-1] if candidates else None
    if args.pbf is None or not args.pbf.is_file():
        parser.error(f"PBF 不存在: {args.pbf}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(f"{args.output.stem}.next{args.output.suffix}")
    temporary.unlink(missing_ok=True)
    if args.output.is_file():
        shutil.copy2(args.output, temporary)
    connection = sqlite3.connect(temporary)
    connection.execute("PRAGMA journal_mode=DELETE")
    connection.execute("PRAGMA synchronous=NORMAL")
    try:
        create_schema(connection)
        handler = PoiHandler(connection)
        print(f"开始解析 {args.pbf.name}，文件大小 {args.pbf.stat().st_size / 1024 / 1024:.1f} MB", flush=True)
        handler.apply_file(str(args.pbf), locations=True, idx="sparse_mem_array")
        handler.flush()
        build_indexes(connection)
        counts = connection.execute("SELECT category, COUNT(*) FROM poi GROUP BY category ORDER BY COUNT(*) DESC").fetchall()
        total = connection.execute("SELECT COUNT(*) FROM poi").fetchone()[0]
        connection.execute("CREATE TABLE IF NOT EXISTS geo_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        metadata = {
            "poi.source": "OpenStreetMap / Geofabrik",
            "poi.license": "ODbL-1.0",
            "poi.pbf_file": args.pbf.name,
            "poi.imported_at": datetime.now(timezone.utc).isoformat(),
            "poi.count": str(total),
        }
        connection.executemany("INSERT OR REPLACE INTO geo_metadata(key, value) VALUES (?, ?)", metadata.items())
        connection.commit()
        if connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("GeoPackage 完整性检查失败")
        connection.close()
        os.replace(temporary, args.output)
        print(f"导入完成，共 {total:,} 条 POI，文件：{args.output}")
        for category, count in counts:
            print(f"  {category}: {count:,}")
    except Exception:
        try:
            connection.rollback()
            connection.close()
        except sqlite3.ProgrammingError:
            pass
        print(f"导入未切换，已构建数据保留在: {temporary}", file=sys.stderr)
        raise

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("导入已取消", file=sys.stderr)
        raise SystemExit(130)
