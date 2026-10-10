from contextlib import closing
import sqlite3

from ..settings import AIRPORTS_PATH
from ..utils.database import placeholders
from ..utils.geopackage import decode_geometry


def _connect():
    if not AIRPORTS_PATH.is_file():
        raise FileNotFoundError(f"Airport GeoPackage not found: {AIRPORTS_PATH}")
    uri = f"file:{AIRPORTS_PATH.as_posix()}?mode=ro"
    connection = sqlite3.connect(uri, uri=True)
    connection.row_factory = sqlite3.Row
    return connection


def _records(rows):
    results = []
    for row in rows:
        longitude, latitude = decode_geometry(row["geom"]).coords[0]
        results.append({
            "icao": row["icao"], "iata": row["iata"], "name": row["name"],
            "city": row["city"], "attr": row["attr"], "longitude": longitude,
            "latitude": latitude,
        })
    return results


def search(keyword, limit=20):
    if not keyword:
        return []
    like, upper = f"%{keyword[:80]}%", keyword.upper()
    with closing(_connect()) as connection:
        rows = connection.execute(
            "SELECT icao, iata, name, city, attr, geom FROM airports "
            "WHERE name LIKE ? OR icao LIKE ? OR iata LIKE ? OR city LIKE ? "
            "ORDER BY CASE WHEN name = ? OR icao = ? OR iata = ? THEN 0 ELSE 1 END, name LIMIT ?",
            (like, f"%{upper}%", f"%{upper}%", like, keyword, upper, upper, limit),
        ).fetchall()
    return _records(rows)


def by_codes(codes, maximum=1000):
    values = list(dict.fromkeys(str(value).strip().upper() for value in (codes or []) if str(value).strip()))[:maximum]
    if not values:
        return []
    marks = placeholders(values)
    with closing(_connect()) as connection:
        rows = connection.execute(
            f"SELECT icao, iata, name, city, attr, geom FROM airports WHERE icao IN ({marks}) OR iata IN ({marks})",
            (*values, *values),
        ).fetchall()
    return _records(rows)
