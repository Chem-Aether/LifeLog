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

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from app.settings import (
    ADMIN_BOUNDARIES_PATH,
)

BOUNDARY_SOURCE_DIR = Path(__file__).resolve().parent.parent / "data" / "source" / "boundaries"
BOUNDARY_FILES = {
    "province": "中国_省.geojson",
    "city": "中国_市.geojson",
    "district": "中国_县.geojson",
}

DIRECT_MUNICIPALITIES = {"11", "12", "31", "50"}
LEVEL_TYPES = {1: "省级行政区", 2: "地级行政区", 3: "县级行政区"}
SRS_WKT = 'GEOGCS["China Geodetic Coordinate System 2000",DATUM["China_2000",SPHEROID["CGCS2000",6378137,298.257222101]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]'


def normalized_code(properties, level):
    if level == 1 and properties.get("adcode") is not None:
        return str(properties["adcode"]).zfill(6)[:2]
    raw = str(properties["gb"])
    six_digits = raw[-6:].zfill(6)
    if level == 1:
        return six_digits[:2]
    if level == 2:
        return six_digits[:2] if six_digits[:2] in DIRECT_MUNICIPALITIES else six_digits[:4]
    return six_digits


def derived_parent(code, level):
    if level == 1:
        return "00"
    if level == 2:
        return code[:2]
    return code[:2] if code[:2] in DIRECT_MUNICIPALITIES else code[:4]


def _existing_attributes(path):
    if not path.is_file():
        return {}
    try:
        with sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True) as connection:
            return {
                (row[0], row[1]): {"type": row[2], "parent_code": row[3]}
                for row in connection.execute(
                    "SELECT code, level, type, parent_code FROM admin_boundaries"
                )
            }
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
                identifier TEXT UNIQUE, description TEXT DEFAULT '',
                last_change DATETIME NOT NULL, min_x DOUBLE, min_y DOUBLE,
                max_x DOUBLE, max_y DOUBLE, srs_id INTEGER,
                FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id)
            );
            CREATE TABLE gpkg_geometry_columns (
                table_name TEXT NOT NULL, column_name TEXT NOT NULL,
                geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL,
                z TINYINT NOT NULL, m TINYINT NOT NULL,
                PRIMARY KEY (table_name, column_name),
                FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id)
            );
            CREATE TABLE gpkg_extensions (
                table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL,
                definition TEXT NOT NULL, scope TEXT NOT NULL,
                UNIQUE (table_name, column_name, extension_name)
            );
            INSERT INTO gpkg_spatial_ref_sys VALUES
                ('Undefined Cartesian', -1, 'NONE', -1, 'undefined', 'undefined cartesian coordinate reference system'),
                ('Undefined Geographic', 0, 'NONE', 0, 'undefined', 'undefined geographic coordinate reference system');
            CREATE TABLE admin_boundaries (
                fid INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL, name TEXT NOT NULL, level INTEGER NOT NULL,
                type TEXT, parent_code TEXT, geom GEOMETRY NOT NULL,
                min_lon REAL NOT NULL, max_lon REAL NOT NULL,
                min_lat REAL NOT NULL, max_lat REAL NOT NULL,
                bbox_area REAL NOT NULL, data_version TEXT NOT NULL,
                source_file TEXT NOT NULL, UNIQUE(level, code)
            );
            CREATE INDEX idx_admin_boundaries_code ON admin_boundaries(code);
            CREATE INDEX idx_admin_boundaries_level_code ON admin_boundaries(level, code);
            CREATE INDEX idx_admin_boundaries_parent ON admin_boundaries(parent_code);
            CREATE VIRTUAL TABLE rtree_admin_boundaries_geom USING rtree(id, minx, maxx, miny, maxy);
        """)
        connection.execute(
            "INSERT INTO gpkg_spatial_ref_sys VALUES (?, 4490, 'EPSG', 4490, ?, 'China Geodetic Coordinate System 2000')",
            ("China Geodetic Coordinate System 2000", SRS_WKT),
        )

        bounds = [float("inf"), float("inf"), float("-inf"), float("-inf")]
        count = 0
        for record in records:
            geometry = record["geometry"]
            if geometry.is_empty:
                continue
            min_lon, min_lat, max_lon, max_lat = geometry.bounds
            bounds = [min(bounds[0], min_lon), min(bounds[1], min_lat),
                      max(bounds[2], max_lon), max(bounds[3], max_lat)]
            cursor = connection.execute(
                "INSERT INTO admin_boundaries "
                "(code,name,level,type,parent_code,geom,min_lon,max_lon,min_lat,max_lat,bbox_area,data_version,source_file) "
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (record["code"], record["name"], record["level"], record.get("type"),
                 record.get("parent_code"), _gpkg_geometry(geometry), min_lon, max_lon,
                 min_lat, max_lat, (max_lon - min_lon) * (max_lat - min_lat),
                 record.get("data_version") or version, record.get("source_file", "")),
            )
            connection.execute(
                "INSERT INTO rtree_admin_boundaries_geom VALUES (?,?,?,?,?)",
                (cursor.lastrowid, min_lon, max_lon, min_lat, max_lat),
            )
            count += 1
        if not count:
            raise ValueError("未导入任何行政区边界")

        changed = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
        connection.execute(
            "INSERT INTO gpkg_contents VALUES "
            "('admin_boundaries','features','行政区边界','省市县级行政区划与边界',?,?,?,?,?,4490)",
            (changed, *bounds),
        )
        connection.execute(
            "INSERT INTO gpkg_geometry_columns VALUES ('admin_boundaries','geom','GEOMETRY',4490,0,0)"
        )
        connection.execute(
            "INSERT INTO gpkg_extensions VALUES "
            "('admin_boundaries','geom','gpkg_rtree_index',"
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
        return count
    except Exception:
        connection.close()
        temporary.unlink(missing_ok=True)
        raise


def import_geojson_files(province, city, district, output, version):
    existing = _existing_attributes(output)
    records = []
    for level, path in ((1, province), (2, city), (3, district)):
        with path.open(encoding="utf-8") as source:
            document = json.load(source)
        if document.get("type") != "FeatureCollection":
            raise ValueError(f"不是 GeoJSON FeatureCollection: {path}")
        for feature in document.get("features", []):
            geometry_json = feature.get("geometry")
            properties = feature.get("properties") or {}
            if not geometry_json or geometry_json.get("type") not in {"Polygon", "MultiPolygon"}:
                continue
            code = normalized_code(properties, level)
            name = properties.get("name")
            if not name:
                continue
            old = existing.get((code, level), {})
            records.append({
                "code": code,
                "name": name,
                "level": level,
                "type": old.get("type") or properties.get("type") or LEVEL_TYPES[level],
                "parent_code": old.get("parent_code") or derived_parent(code, level),
                "geometry": shape(geometry_json),
                "data_version": version,
                "source_file": path.name,
            })
    unique_records = {(record["level"], record["code"]): record for record in records}
    return write_package(unique_records.values(), output, version)


def main():
    parser = argparse.ArgumentParser(description="将省市县 GeoJSON 导入独立行政区 GeoPackage")
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
    print(f"导入完成: {count} 个行政区边界, {args.output}")


if __name__ == "__main__":
    main()
