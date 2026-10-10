import sqlite3
from contextlib import closing
from functools import lru_cache

from shapely.geometry import Point, mapping

from ..settings import ADMIN_BOUNDARIES_PATH
from ..utils.geopackage import decode_geometry


def _connect():
    if not ADMIN_BOUNDARIES_PATH.is_file():
        raise FileNotFoundError(f"Administrative boundary GeoPackage not found: {ADMIN_BOUNDARIES_PATH}")
    uri = f"file:{ADMIN_BOUNDARIES_PATH.as_posix()}?mode=ro"
    connection = sqlite3.connect(uri, uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def _record(connection, code):
    row = connection.execute(
        "SELECT code, name, level, type, parent_code AS parentCode "
        "FROM admin_boundaries WHERE code = ? ORDER BY level DESC LIMIT 1",
        (str(code),),
    ).fetchone()
    return dict(row) if row else None


def _full_name(connection, code):
    names, visited = [], set()
    while code and code not in {"0", "00"} and code not in visited:
        visited.add(code)
        row = _record(connection, code)
        if not row:
            break
        names.append(row["name"])
        code = row["parentCode"]
    return "".join(reversed(names))


def region_record(code):
    with closing(_connect()) as connection:
        return _record(connection, code)


def full_name(code):
    with closing(_connect()) as connection:
        return _full_name(connection, code)


def boundary_row(code):
    with closing(_connect()) as connection:
        row = connection.execute(
            "SELECT * FROM admin_boundaries WHERE code = ? ORDER BY level DESC LIMIT 1",
            (str(code),),
        ).fetchone()
        return dict(row) if row else None


def boundary_feature(row):
    if not row:
        return None
    geometry = decode_geometry(row["geom"])
    return {
        "type": "Feature",
        "id": row["code"],
        "bbox": [row["min_lon"], row["min_lat"], row["max_lon"], row["max_lat"]],
        "properties": {
            "code": row["code"], "name": row["name"], "level": row["level"],
            "parentCode": row["parent_code"], "dataVersion": row["data_version"],
        },
        "geometry": mapping(geometry),
    }


def search(keyword, level=0, limit=20):
    like = f"%{keyword[:80]}%"
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT code, name, level, type, parent_code AS parentCode FROM admin_boundaries "
            "WHERE (name LIKE ? OR code LIKE ?) AND (? = 0 OR level = ?) "
            "ORDER BY CASE WHEN name = ? OR code = ? THEN 0 ELSE 1 END, level, code LIMIT ?",
            (like, like, level, level, keyword, keyword, limit),
        ).fetchall()
        results = [dict(row) for row in rows]
        for row in results:
            row["fullName"] = _full_name(connection, row["code"])
        return results


def search_boundaries(keyword, level=0, limit=20):
    like = f"%{keyword[:80]}%"
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT * FROM admin_boundaries WHERE (name LIKE ? OR code LIKE ?) "
            "AND (? = 0 OR level = ?) "
            "ORDER BY CASE WHEN name = ? OR code = ? THEN 0 ELSE 1 END, level DESC, name LIMIT ?",
            (like, like, level, level, keyword, keyword, limit),
        ).fetchall()
        results = []
        for raw in rows:
            row = dict(raw)
            point = decode_geometry(row["geom"]).representative_point()
            results.append({
                "type": "region", "id": f"region:{row['level']}:{row['code']}", "name": row["name"],
                "displayName": _full_name(connection, row["code"]) or row["name"],
                "longitude": point.x, "latitude": point.y,
                "region": {"code": row["code"], "level": row["level"], "parentCode": row["parent_code"]},
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

    levels = {}
    for row in matched:
        levels.setdefault(row["level"], row)
    province, city, district = levels.get(1), levels.get(2), levels.get(3)
    if district and (district["code"] in {province and province["code"], city and city["code"]}
                     or district["name"] in {province and province["name"], city and city["name"]}):
        district = None

    def summary(row):
        return {"code": row["code"], "name": row["name"]} if row else None

    names = []
    for row in (province, city, district):
        if row and (not names or names[-1] != row["name"]):
            names.append(row["name"])
    source = district or city or province
    return {
        "longitude": longitude, "latitude": latitude, "province": summary(province), "city": summary(city),
        "district": summary(district), "formattedRegion": "".join(names),
        "dataVersion": source["data_version"] if source else None,
    }


def reverse(longitude, latitude):
    return _reverse_cached(round(longitude, 5), round(latitude, 5))
