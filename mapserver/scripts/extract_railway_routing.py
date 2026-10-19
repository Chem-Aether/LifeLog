#!/usr/bin/env python3
"""Extract a topology-preserving, editable railway GeoPackage from an OSM PBF."""
import argparse
import json
import math
import os
import sqlite3
import sys
import tempfile
from pathlib import Path

import osmium
from shapely.geometry import LineString, Point
from shapely import to_wkb
from tqdm import tqdm

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.settings import RAILWAY_NETWORK_PATH, RAILWAY_ROUTING_PATH

DEFAULT_PBF = ROOT / "data" / "source" / "osm" / "china-260919.osm.pbf"
BATCH_SIZE = 5000


def gpkg_blob(geometry):
    return b"GP\x00\x01" + (4326).to_bytes(4, "little", signed=True) + to_wkb(geometry, byte_order=1)


def init_gpkg(connection):
    connection.executescript("""
        PRAGMA application_id=1196437808;
        PRAGMA user_version=10300;
        PRAGMA journal_mode=WAL;
        PRAGMA synchronous=NORMAL;
        CREATE TABLE gpkg_spatial_ref_sys (
          srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY, organization TEXT NOT NULL,
          organization_coordsys_id INTEGER NOT NULL, definition TEXT NOT NULL, description TEXT);
        INSERT INTO gpkg_spatial_ref_sys VALUES
          ('Undefined Cartesian',-1,'NONE',-1,'undefined','undefined cartesian coordinate reference system'),
          ('Undefined Geographic',0,'NONE',0,'undefined','undefined geographic coordinate reference system'),
          ('WGS 84 geodetic',4326,'EPSG',4326,
           'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]',
           'longitude/latitude coordinates in decimal degrees on the WGS 84 spheroid');
        CREATE TABLE gpkg_contents (
          table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL, identifier TEXT UNIQUE,
          description TEXT DEFAULT '', last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
          min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER,
          FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id));
        CREATE TABLE gpkg_geometry_columns (
          table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL,
          srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL,
          PRIMARY KEY (table_name,column_name), FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id));
        CREATE TABLE gpkg_extensions (
          table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL,
          definition TEXT NOT NULL, scope TEXT NOT NULL,
          UNIQUE(table_name,column_name,extension_name));
        CREATE TABLE railway_nodes (
          fid INTEGER PRIMARY KEY AUTOINCREMENT, geom POINT NOT NULL, osm_node_id INTEGER NOT NULL UNIQUE);
        CREATE TABLE railway_edges (
          fid INTEGER PRIMARY KEY AUTOINCREMENT, geom LINESTRING NOT NULL, osm_way_id INTEGER NOT NULL,
          from_node INTEGER NOT NULL, to_node INTEGER NOT NULL, distance_m REAL NOT NULL,
          from_way_endpoint INTEGER NOT NULL, to_way_endpoint INTEGER NOT NULL,
          name TEXT, ref TEXT, railway TEXT, usage TEXT, service TEXT, layer REAL NOT NULL,
          bridge TEXT, tunnel TEXT, electrified TEXT, gauge TEXT, highspeed TEXT);
        CREATE INDEX idx_railway_edges_from ON railway_edges(from_node);
        CREATE INDEX idx_railway_edges_to ON railway_edges(to_node);
        CREATE INDEX idx_railway_edges_way ON railway_edges(osm_way_id);
        CREATE VIRTUAL TABLE rtree_railway_edges_geom USING rtree(id,minx,maxx,miny,maxy);
        CREATE TABLE railway_stations (
          fid INTEGER PRIMARY KEY, geom POINT NOT NULL, name TEXT, code TEXT, pinyin TEXT, city TEXT, source TEXT);
        CREATE TABLE railway_relations (
          osm_relation_id INTEGER PRIMARY KEY, name TEXT, ref TEXT, route TEXT, network TEXT, operator TEXT);
        CREATE TABLE railway_relation_members (
          osm_relation_id INTEGER NOT NULL, member_type TEXT NOT NULL, member_id INTEGER NOT NULL,
          role TEXT, PRIMARY KEY(osm_relation_id,member_type,member_id,role));
        CREATE INDEX idx_railway_relation_member ON railway_relation_members(member_type,member_id);
    """)
    for name, data_type, geometry_type in (
        ("railway_nodes", "features", "POINT"), ("railway_edges", "features", "LINESTRING"),
        ("railway_stations", "features", "POINT"), ("railway_relations", "attributes", None),
        ("railway_relation_members", "attributes", None),
    ):
        connection.execute("INSERT INTO gpkg_contents(table_name,data_type,identifier,srs_id) VALUES(?,?,?,?)",
                           (name, data_type, name, 4326 if geometry_type else None))
        if geometry_type:
            connection.execute("INSERT INTO gpkg_geometry_columns VALUES(?,?,?,?,0,0)",
                               (name, "geom", geometry_type, 4326))
    connection.execute(
        "INSERT INTO gpkg_extensions VALUES('railway_edges','geom','gpkg_rtree_index',"
        "'http://www.geopackage.org/spec/#extension_rtree','write-only')")


def add_qgis_edit_triggers(connection):
    connection.executescript("""
        CREATE TRIGGER rtree_railway_edges_geom_insert AFTER INSERT ON railway_edges
        WHEN (NEW.geom NOT NULL AND NOT ST_IsEmpty(NEW.geom)) BEGIN
          INSERT OR REPLACE INTO rtree_railway_edges_geom VALUES
          (NEW.fid,ST_MinX(NEW.geom),ST_MaxX(NEW.geom),ST_MinY(NEW.geom),ST_MaxY(NEW.geom));
        END;
        CREATE TRIGGER rtree_railway_edges_geom_update1 BEFORE UPDATE OF geom ON railway_edges
        WHEN OLD.fid=NEW.fid AND (NEW.geom IS NULL OR ST_IsEmpty(NEW.geom)) BEGIN
          DELETE FROM rtree_railway_edges_geom WHERE id=OLD.fid;
        END;
        CREATE TRIGGER rtree_railway_edges_geom_update2 AFTER UPDATE OF geom ON railway_edges
        WHEN OLD.fid=NEW.fid AND (NEW.geom NOT NULL AND NOT ST_IsEmpty(NEW.geom)) BEGIN
          INSERT OR REPLACE INTO rtree_railway_edges_geom VALUES
          (NEW.fid,ST_MinX(NEW.geom),ST_MaxX(NEW.geom),ST_MinY(NEW.geom),ST_MaxY(NEW.geom));
        END;
        CREATE TRIGGER rtree_railway_edges_geom_update3 AFTER UPDATE ON railway_edges
        WHEN OLD.fid != NEW.fid BEGIN
          DELETE FROM rtree_railway_edges_geom WHERE id=OLD.fid;
          INSERT OR REPLACE INTO rtree_railway_edges_geom VALUES
          (NEW.fid,ST_MinX(NEW.geom),ST_MaxX(NEW.geom),ST_MinY(NEW.geom),ST_MaxY(NEW.geom));
        END;
        CREATE TRIGGER rtree_railway_edges_geom_delete AFTER DELETE ON railway_edges
        WHEN OLD.geom NOT NULL BEGIN
          DELETE FROM rtree_railway_edges_geom WHERE id=OLD.fid;
        END;
    """)


def copy_stations(connection, source_path):
    if not source_path.is_file():
        print(f"未找到既有车站图层：{source_path}；输出仍包含完整轨道网。", flush=True)
        return
    source = sqlite3.connect(f"file:{source_path.as_posix()}?mode=ro", uri=True)
    try:
        rows = source.execute("SELECT fid,geom,name,code,pinyin,city,source FROM railway_stations")
        connection.executemany("INSERT INTO railway_stations VALUES(?,?,?,?,?,?,?)", rows)
        connection.commit()
    finally:
        source.close()


def haversine(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, (*a, *b))
    dlon, dlat = lon2 - lon1, lat2 - lat1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 12742017.6 * math.asin(math.sqrt(h))


class PbfWayCollector(osmium.SimpleHandler):
    def __init__(self, staging, output, progress):
        super().__init__()
        self.staging = staging
        self.output = output
        self.progress = progress
        self.pending = 0
        self.stats = {"objects": 0, "nodes": 0, "ways": 0, "edges": 0, "relations": 0}

    def _tick(self, key=None):
        self.stats["objects"] += 1
        if key:
            self.stats[key] += 1
        self.pending += 1
        if self.pending >= 20000:
            self.progress.update(self.pending)
            self.pending = 0
            self.progress.set_postfix(rail_ways=f"{self.stats['ways']:,}", relations=f"{self.stats['relations']:,}", refresh=False)

    def node(self, _node):
        self._tick()

    def way(self, way):
        self._tick()
        if way.tags.get("railway") not in {"rail", "narrow_gauge"}:
            return
        self.stats["ways"] += 1
        self.staging.execute("INSERT INTO rail_ways VALUES(?,?,?)",
                              (way.id, json.dumps([node.ref for node in way.nodes]), json.dumps(dict(way.tags))))

    def relation(self, relation):
        self._tick()
        route = relation.tags.get("route")
        if route not in {"train", "railway"}:
            return
        self.stats["relations"] += 1
        tags = relation.tags
        self.output.execute("INSERT OR REPLACE INTO railway_relations VALUES(?,?,?,?,?,?)",
                        (relation.id, tags.get("name"), tags.get("ref"), route, tags.get("network"), tags.get("operator")))
        self.output.executemany("INSERT OR IGNORE INTO railway_relation_members VALUES(?,?,?,?)",
                            ((relation.id, member.type, member.ref, member.role) for member in relation.members))

    def finish(self):
        if self.pending:
            self.progress.update(self.pending)
        self.progress.close()


class PbfNodeCollector(osmium.SimpleHandler):
    def __init__(self, staging, node_ids, progress):
        super().__init__()
        self.staging = staging
        self.node_ids = node_ids
        self.progress = progress
        self.pending = 0
        self.found = 0

    def node(self, node):
        self.pending += 1
        if node.id in self.node_ids:
            self.staging.execute("INSERT OR IGNORE INTO rail_node_coordinates VALUES(?,?,?)",
                                 (node.id, node.lon, node.lat))
            self.found += 1
        if self.pending >= 20000:
            self.progress.update(self.pending)
            self.pending = 0
            self.progress.set_postfix(matched=f"{self.found:,}", refresh=False)

    def finish(self):
        if self.pending:
            self.progress.update(self.pending)
        self.staging.commit()
        self.progress.close()


def write_way(output, way_id, node_ids, tags, coordinates):
    try:
        level = float(tags.get("layer", "0")) if tags.get("layer") is not None else (
            1.0 if tags.get("bridge") not in (None, "no") else -1.0 if tags.get("tunnel") not in (None, "no") else 0.0)
    except ValueError:
        level = 0.0
    line_name = tags.get("name:zh-Hans") or tags.get("name:zh") or tags.get("name")
    way_nodes = [(node_id, coordinates.get(node_id)) for node_id in node_ids]
    for node_id, point in way_nodes:
        if point is not None:
            output.execute("INSERT OR IGNORE INTO railway_nodes(geom,osm_node_id) VALUES(?,?)",
                           (gpkg_blob(Point(point)), node_id))
    inserted = 0
    for index, ((from_node, first), (to_node, second)) in enumerate(zip(way_nodes, way_nodes[1:])):
        if first is None or second is None or from_node == to_node:
            continue
        length = haversine(first, second)
        if length <= 0:
            continue
        geometry = gpkg_blob(LineString([first, second]))
        cursor = output.execute(
            "INSERT INTO railway_edges(geom,osm_way_id,from_node,to_node,distance_m,from_way_endpoint,to_way_endpoint,name,ref,railway,usage,service,layer,bridge,tunnel,electrified,gauge,highspeed) "
            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (geometry, way_id, from_node, to_node, length, int(index == 0), int(index + 1 == len(way_nodes) - 1),
             line_name, tags.get("ref"), tags.get("railway"), tags.get("usage"), tags.get("service"), level,
             tags.get("bridge"), tags.get("tunnel"), tags.get("electrified"), tags.get("gauge"), tags.get("highspeed")))
        edge_id = cursor.lastrowid
        output.execute("INSERT INTO rtree_railway_edges_geom VALUES(?,?,?,?,?)",
                       (edge_id, min(first[0], second[0]), max(first[0], second[0]), min(first[1], second[1]), max(first[1], second[1])))
        inserted += 1
    return inserted


def main():
    parser = argparse.ArgumentParser(description="从 OSM PBF 提取可在 QGIS 编辑查看的拓扑铁路网 GeoPackage")
    parser.add_argument("--pbf", type=Path, default=DEFAULT_PBF)
    parser.add_argument("--output", type=Path, default=RAILWAY_ROUTING_PATH)
    parser.add_argument("--stations", type=Path, default=RAILWAY_NETWORK_PATH)
    args = parser.parse_args()
    if not args.pbf.is_file():
        parser.error(f"找不到 PBF 文件：{args.pbf}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temp_path = args.output.with_name(f"{args.output.stem}.building.gpkg")
    if temp_path.exists():
        temp_path.unlink()

    with tempfile.TemporaryDirectory(prefix="railway-node-index-", dir=args.output.parent) as temp_dir:
        staging_path = Path(temp_dir) / "railway-staging.sqlite"
        staging = sqlite3.connect(staging_path)
        staging.executescript("""
            PRAGMA journal_mode=WAL;
            CREATE TABLE rail_ways(osm_way_id INTEGER PRIMARY KEY,nodes_json TEXT NOT NULL,tags_json TEXT NOT NULL);
            CREATE TABLE rail_node_coordinates(osm_node_id INTEGER PRIMARY KEY,longitude REAL NOT NULL,latitude REAL NOT NULL);
        """)
        connection = sqlite3.connect(temp_path)
        init_gpkg(connection)
        copy_stations(connection, args.stations)
        handler = PbfWayCollector(staging, connection, tqdm(desc="PBF objects", unit="obj", dynamic_ncols=True, mininterval=1.0))
        try:
            print(f"源文件：{args.pbf} ({args.pbf.stat().st_size / 1024**3:.2f} GiB)", flush=True)
            print("Stage 1/3: Scan railway ways, relations, and ordered node IDs.", flush=True)
            handler.apply_file(str(args.pbf))
            handler.finish()
            staging.commit()
            node_ids = osmium.index.IdSet()
            for (nodes_json,) in staging.execute("SELECT nodes_json FROM rail_ways"):
                for node_id in json.loads(nodes_json):
                    node_ids.set(node_id)
            way_count = handler.stats["ways"]
            print(f"Stage 2/3: Rescan nodes; retain coordinates for {len(node_ids):,} railway nodes.", flush=True)
            node_collector = PbfNodeCollector(staging, node_ids,
                                              tqdm(desc="Rail nodes", unit="node", dynamic_ncols=True, mininterval=1.0))
            node_collector.apply_file(str(args.pbf))
            node_collector.finish()
            coordinates = {node_id: (lon, lat) for node_id, lon, lat in staging.execute(
                "SELECT osm_node_id,longitude,latitude FROM rail_node_coordinates")}
            print("Stage 3/3: Build topological edges and write GeoPackage layers.", flush=True)
            build_progress = tqdm(total=way_count, desc="Write rail ways", unit="way", dynamic_ncols=True, mininterval=1.0)
            edges_written = 0
            for way_id, nodes_json, tags_json in staging.execute("SELECT osm_way_id,nodes_json,tags_json FROM rail_ways"):
                edges_written += write_way(connection, way_id, json.loads(nodes_json), json.loads(tags_json), coordinates)
                build_progress.update(1)
                if edges_written and edges_written % BATCH_SIZE < 100:
                    connection.commit()
            build_progress.close()
            handler.stats["edges"] = edges_written
            handler.stats["nodes"] = len(coordinates)
            connection.commit()
            add_qgis_edit_triggers(connection)
            connection.execute("ANALYZE")
            connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            connection.execute("PRAGMA journal_mode=DELETE")
            connection.commit()
            connection.close()
            staging.close()
            os.replace(temp_path, args.output)
        except BaseException:
            connection.close()
            staging.close()
            raise

    print(f"Done: {args.output}", flush=True)
    print(f"Railway ways: {handler.stats['ways']:,}; topology nodes: {handler.stats['nodes']:,}; track edges: {handler.stats['edges']:,}; railway relations: {handler.stats['relations']:,}", flush=True)
    print("Layers: railway_edges, railway_nodes, railway_stations; tables: railway_relations, railway_relation_members.", flush=True)


if __name__ == "__main__":
    main()
