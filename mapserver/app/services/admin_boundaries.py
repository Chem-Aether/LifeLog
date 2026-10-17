import sqlite3
from contextlib import closing
from functools import lru_cache

from shapely.geometry import Point, mapping

from ..settings import ADMIN_BOUNDARIES_PATH
from ..utils.geopackage import decode_geometry


def _connect():
    if not ADMIN_BOUNDARIES_PATH.is_file():
        raise FileNotFoundError(f"Administrative boundary GeoPackage not found: {ADMIN_BOUNDARIES_PATH}")
    connection = sqlite3.connect(f"file:{ADMIN_BOUNDARIES_PATH.as_posix()}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def _node(row):
    return {
        "id": row["node_id"], "parentId": row["parent_id"],
        "countryCode": row["country_code"], "regionCode": row["region_code"],
        "level": row["level"], "name": row["name"], "type": row["type"],
    }


def _record(connection, node_id):
    row = connection.execute("SELECT * FROM admin_boundaries WHERE node_id = ?", (str(node_id),)).fetchone()
    return dict(row) if row else None


def _path(connection, node_id):
    chain, visited = [], set()
    while node_id and node_id not in visited:
        visited.add(node_id)
        row = _record(connection, node_id)
        if not row:
            break
        chain.append(row)
        node_id = row["parent_id"]
    return list(reversed(chain))


@lru_cache(maxsize=5000)
def _region_record_cached(node_id):
    with closing(_connect()) as connection:
        row = _record(connection, node_id)
        if not row:
            return None
        return {"path": [_node(item) for item in _path(connection, node_id)], "dataVersion": row["data_version"]}


def region_record(node_id):
    return _region_record_cached(str(node_id))


def region_records(node_ids):
    return {node_id: record for node_id in dict.fromkeys(str(value) for value in node_ids if value)
            if (record := region_record(node_id)) is not None}


def boundary_row(node_id):
    with closing(_connect()) as connection:
        return _record(connection, node_id)


def boundary_feature(row):
    if not row:
        return None
    geometry = decode_geometry(row["geom"])
    return {
        "type": "Feature", "id": row["node_id"],
        "bbox": [row["min_lon"], row["min_lat"], row["max_lon"], row["max_lat"]],
        "properties": {**_node(row), "dataVersion": row["data_version"]},
        "geometry": mapping(geometry),
    }


def search(keyword, level=0, country_code=None, limit=20):
    like = f"%{keyword[:80]}%"
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT * FROM admin_boundaries WHERE (name LIKE ? OR region_code LIKE ?) "
            "AND (? = 0 OR level = ?) AND (? IS NULL OR country_code = ?) "
            "ORDER BY CASE WHEN name = ? OR region_code = ? THEN 0 ELSE 1 END, level, name LIMIT ?",
            (like, like, level, level, country_code, country_code, keyword, keyword, limit),
        ).fetchall()
        results = []
        for raw in rows:
            row = dict(raw)
            results.append({
                "path": [_node(item) for item in _path(connection, row["node_id"])],
                "dataVersion": row["data_version"],
            })
        return results


def search_boundaries(keyword, level=0, limit=20):
    like = f"%{keyword[:80]}%"
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT * FROM admin_boundaries WHERE (name LIKE ? OR region_code LIKE ?) "
            "AND (? = 0 OR level = ?) ORDER BY level DESC, name LIMIT ?",
            (like, like, level, level, limit),
        ).fetchall()
        results = []
        for raw in rows:
            row = dict(raw)
            point = decode_geometry(row["geom"]).representative_point()
            results.append({
                "type": "region", "id": row["node_id"], "name": row["name"],
                "longitude": point.x, "latitude": point.y, "region": _node(row),
            })
        return results


@lru_cache(maxsize=5000)
def _reverse_cached(longitude, latitude):
    point = Point(longitude, latitude)
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT b.* FROM rtree_admin_boundaries_geom r "
            "JOIN admin_boundaries b ON b.fid = r.id "
            "WHERE r.minx <= ? AND r.maxx >= ? AND r.miny <= ? AND r.maxy >= ? "
            "ORDER BY b.level DESC, b.bbox_area ASC",
            (longitude, longitude, latitude, latitude),
        ).fetchall()
        matched = [dict(row) for row in rows if decode_geometry(row["geom"]).covers(point)]
        matched.sort(key=lambda row: (-row["level"], row["bbox_area"], row["node_id"]))
        chain = _path(connection, matched[0]["node_id"]) if matched else []
    nodes = [_node(row) for row in chain]
    return {
        "longitude": longitude, "latitude": latitude, "path": nodes,
        "dataVersion": chain[-1]["data_version"] if chain else None,
    }


def reverse(longitude, latitude):
    return _reverse_cached(round(longitude, 5), round(latitude, 5))
