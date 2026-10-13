#!/usr/bin/env python3
import argparse
import json
import os
import sqlite3
import struct
import sys
from datetime import datetime, timezone
from pathlib import Path

from shapely import to_wkb
from shapely.geometry import shape
from shapely.ops import unary_union

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from app.settings import ADMIN_BOUNDARIES_PATH

BOUNDARY_SOURCE_DIR = Path(__file__).resolve().parent.parent / "data" / "source" / "boundaries"
BOUNDARY_FILES = {"province": "中国_省.geojson", "city": "中国_市.geojson", "district": "中国_县.geojson"}
DIRECT_MUNICIPALITIES = {"11", "12", "31", "50"}
LEVEL_TYPES = {1: "省级行政区", 2: "地级行政区", 3: "县级行政区"}
COUNTRY_CODE = "CN"
SRS_WKT = 'GEOGCS["China Geodetic Coordinate System 2000",DATUM["China_2000",SPHEROID["CGCS2000",6378137,298.257222101]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]'


def normalized_code(properties, level):
    if level == 1 and properties.get("adcode") is not None:
        return str(properties["adcode"]).zfill(6)[:2]
    six_digits = str(properties["gb"])[-6:].zfill(6)
    if level == 1:
        return six_digits[:2]
    if level == 2:
        return six_digits[:2] if six_digits[:2] in DIRECT_MUNICIPALITIES else six_digits[:4]
    return six_digits


def parent_code(code, level):
    if level == 1:
        return "CN"
    if level == 2:
        return code[:2]
    return code[:2] if code[:2] in DIRECT_MUNICIPALITIES else code[:4]


def node_id(level, region_code):
    return f"{COUNTRY_CODE}:{level}:{region_code}"


def _existing_types(path):
    if not path.is_file():
        return {}
    try:
        with sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True) as connection:
            columns = {row[1] for row in connection.execute("PRAGMA table_info(admin_boundaries)")}
            code_column = "region_code" if "region_code" in columns else "code"
            return {(row[0], row[1]): row[2] for row in connection.execute(
                f"SELECT {code_column}, level, type FROM admin_boundaries"
            )}
    except sqlite3.Error:
        return {}


def _gpkg_geometry(geometry):
    return b"GP\x00\x01" + struct.pack("<i", 4490) + to_wkb(geometry, byte_order=1, output_dimension=2)


def write_package(records, output, version):
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f"{output.stem}.next{output.suffix}")
    temporary.unlink(missing_ok=True)
    connection = sqlite3.connect(temporary)
    try:
        connection.executescript("""
            PRAGMA application_id = 1196444487;
            PRAGMA user_version = 10300;
            PRAGMA foreign_keys = ON;
            CREATE TABLE gpkg_spatial_ref_sys (
                srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY,
                organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL,
                definition TEXT NOT NULL, description TEXT
            );
            CREATE TABLE gpkg_contents (
                table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL,
                identifier TEXT UNIQUE, description TEXT DEFAULT '', last_change DATETIME NOT NULL,
                min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER,
                FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id)
            );
            CREATE TABLE gpkg_geometry_columns (
                table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL,
                srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL,
                PRIMARY KEY (table_name, column_name), FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id)
            );
            CREATE TABLE gpkg_extensions (
                table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL,
                definition TEXT NOT NULL, scope TEXT NOT NULL, UNIQUE (table_name, column_name, extension_name)
            );
            INSERT INTO gpkg_spatial_ref_sys VALUES
                ('Undefined Cartesian', -1, 'NONE', -1, 'undefined', 'undefined cartesian coordinate reference system'),
                ('Undefined Geographic', 0, 'NONE', 0, 'undefined', 'undefined geographic coordinate reference system');
            CREATE TABLE admin_boundaries (
                fid INTEGER PRIMARY KEY AUTOINCREMENT,
                node_id TEXT NOT NULL UNIQUE,
                parent_id TEXT REFERENCES admin_boundaries(node_id),
                country_code TEXT NOT NULL,
                region_code TEXT,
                level INTEGER NOT NULL,
                name TEXT NOT NULL,
                type TEXT,
                geom GEOMETRY NOT NULL,
                min_lon REAL NOT NULL, max_lon REAL NOT NULL, min_lat REAL NOT NULL, max_lat REAL NOT NULL,
                bbox_area REAL NOT NULL, data_version TEXT NOT NULL, source_file TEXT NOT NULL,
                UNIQUE(country_code, level, region_code)
            );
            CREATE INDEX idx_admin_boundaries_parent ON admin_boundaries(parent_id);
            CREATE INDEX idx_admin_boundaries_country_level ON admin_boundaries(country_code, level);
            CREATE INDEX idx_admin_boundaries_region_code ON admin_boundaries(region_code);
            CREATE VIRTUAL TABLE rtree_admin_boundaries_geom USING rtree(id, minx, maxx, miny, maxy);
        """)
        connection.execute(
            "INSERT INTO gpkg_spatial_ref_sys VALUES (?, 4490, 'EPSG', 4490, ?, 'China Geodetic Coordinate System 2000')",
            ("China Geodetic Coordinate System 2000", SRS_WKT),
        )
        bounds = [float("inf"), float("inf"), float("-inf"), float("-inf")]
        for record in sorted(records, key=lambda item: item["level"]):
            geometry = record["geometry"]
            if geometry.is_empty:
                continue
            min_lon, min_lat, max_lon, max_lat = geometry.bounds
            bounds = [min(bounds[0], min_lon), min(bounds[1], min_lat),
                      max(bounds[2], max_lon), max(bounds[3], max_lat)]
            cursor = connection.execute(
                "INSERT INTO admin_boundaries (node_id,parent_id,country_code,region_code,level,name,type,geom,"
                "min_lon,max_lon,min_lat,max_lat,bbox_area,data_version,source_file) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (record["node_id"], record.get("parent_id"), record["country_code"], record.get("region_code"),
                 record["level"], record["name"], record.get("type"), _gpkg_geometry(geometry),
                 min_lon, max_lon, min_lat, max_lat, (max_lon - min_lon) * (max_lat - min_lat),
                 record.get("data_version") or version, record.get("source_file", "")),
            )
            connection.execute("INSERT INTO rtree_admin_boundaries_geom VALUES (?,?,?,?,?)",
                               (cursor.lastrowid, min_lon, max_lon, min_lat, max_lat))
        changed = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
        connection.execute(
            "INSERT INTO gpkg_contents VALUES ('admin_boundaries','features','行政区节点','国家及行政区树节点和边界',?,?,?,?,?,4490)",
            (changed, *bounds),
        )
        connection.execute("INSERT INTO gpkg_geometry_columns VALUES ('admin_boundaries','geom','GEOMETRY',4490,0,0)")
        connection.execute(
            "INSERT INTO gpkg_extensions VALUES ('admin_boundaries','geom','gpkg_rtree_index',"
            "'http://www.geopackage.org/spec/#extension_rtree','write-only')"
        )
        connection.executescript("""
            CREATE TRIGGER rtree_admin_boundaries_geom_update1 AFTER UPDATE OF geom ON admin_boundaries
            WHEN OLD.fid = NEW.fid AND (NEW.geom IS NULL OR ST_IsEmpty(NEW.geom)) BEGIN
                DELETE FROM rtree_admin_boundaries_geom WHERE id = OLD.fid;
            END;
            CREATE TRIGGER rtree_admin_boundaries_geom_update2 AFTER UPDATE OF geom ON admin_boundaries
            WHEN OLD.fid = NEW.fid AND NEW.geom NOT NULL AND NOT ST_IsEmpty(NEW.geom) BEGIN
                INSERT OR REPLACE INTO rtree_admin_boundaries_geom VALUES
                (NEW.fid, ST_MinX(NEW.geom), ST_MaxX(NEW.geom), ST_MinY(NEW.geom), ST_MaxY(NEW.geom));
            END;
            CREATE TRIGGER rtree_admin_boundaries_geom_insert AFTER INSERT ON admin_boundaries
            WHEN NEW.geom NOT NULL AND NOT ST_IsEmpty(NEW.geom) BEGIN
                INSERT OR REPLACE INTO rtree_admin_boundaries_geom VALUES
                (NEW.fid, ST_MinX(NEW.geom), ST_MaxX(NEW.geom), ST_MinY(NEW.geom), ST_MaxY(NEW.geom));
            END;
            CREATE TRIGGER rtree_admin_boundaries_geom_delete AFTER DELETE ON admin_boundaries BEGIN
                DELETE FROM rtree_admin_boundaries_geom WHERE id = OLD.fid;
            END;
        """)
        connection.commit()
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"GeoPackage 完整性检查失败: {integrity}")
        connection.close()
        os.replace(temporary, output)
        return len(records)
    except Exception:
        connection.close()
        temporary.unlink(missing_ok=True)
        raise


def import_geojson_files(province, city, district, output, version):
    old_types = _existing_types(output)
    old_types.update(_existing_types(ADMIN_BOUNDARIES_PATH))
    records, province_geometries = [], []
    for level, path in ((1, province), (2, city), (3, district)):
        with path.open(encoding="utf-8") as source:
            document = json.load(source)
        if document.get("type") != "FeatureCollection":
            raise ValueError(f"不是 GeoJSON FeatureCollection: {path}")
        seen = set()
        for feature in document.get("features", []):
            geometry_json = feature.get("geometry")
            properties = feature.get("properties") or {}
            if not geometry_json or geometry_json.get("type") not in {"Polygon", "MultiPolygon"} or not properties.get("name"):
                continue
            code = normalized_code(properties, level)
            if code in seen:
                continue
            seen.add(code)
            geometry = shape(geometry_json)
            if level == 1:
                province_geometries.append(geometry)
            parent = parent_code(code, level)
            records.append({
                "node_id": node_id(level, code),
                "parent_id": "CN:0:CN" if level == 1 else node_id(level - 1, parent),
                "country_code": COUNTRY_CODE, "region_code": code, "name": properties["name"], "level": level,
                "type": old_types.get((code, level)) or properties.get("type") or LEVEL_TYPES[level],
                "geometry": geometry, "data_version": version, "source_file": path.name,
            })
    if not province_geometries:
        raise ValueError("省级边界为空，无法构建国家节点")
    available = {(record["level"], record["region_code"]): record["node_id"] for record in records}
    for record in records:
        if record["level"] == 1:
            record["parent_id"] = "CN:0:CN"
            continue
        parent = parent_code(record["region_code"], record["level"])
        parent_level = record["level"] - 1
        while parent_level > 0 and (parent_level, parent) not in available:
            parent_level -= 1
            if parent_level == 1:
                parent = parent[:2]
        record["parent_id"] = available.get((parent_level, parent), "CN:0:CN")
    records.append({
        "node_id": "CN:0:CN", "parent_id": None, "country_code": COUNTRY_CODE,
        "region_code": None, "name": "中国", "level": 0, "type": "country",
        "geometry": unary_union(province_geometries), "data_version": version, "source_file": "derived:province",
    })
    return write_package(records, output, version)


def main():
    parser = argparse.ArgumentParser(description="将省市县 GeoJSON 导入带父子节点关系的行政区 GeoPackage")
    parser.add_argument("--province", type=Path, default=BOUNDARY_SOURCE_DIR / BOUNDARY_FILES["province"])
    parser.add_argument("--city", type=Path, default=BOUNDARY_SOURCE_DIR / BOUNDARY_FILES["city"])
    parser.add_argument("--district", type=Path, default=BOUNDARY_SOURCE_DIR / BOUNDARY_FILES["district"])
    parser.add_argument("--output", type=Path, default=ADMIN_BOUNDARIES_PATH)
    parser.add_argument("--version", default=datetime.now(timezone.utc).date().isoformat())
    args = parser.parse_args()
    for path in (args.province, args.city, args.district):
        if not path.is_file():
            parser.error(f"文件不存在: {path}")
    count = import_geojson_files(args.province, args.city, args.district, args.output, args.version)
    print(f"导入完成: {count} 个行政区树节点, {args.output}")


if __name__ == "__main__":
    main()
