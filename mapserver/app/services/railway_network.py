from contextlib import closing
import sqlite3

from ..settings import RAILWAY_NETWORK_PATH
from ..utils.geopackage import decode_geometry


def connect_network():
    if not RAILWAY_NETWORK_PATH.is_file():
        raise FileNotFoundError(f"Railway network not found: {RAILWAY_NETWORK_PATH}")
    uri = f"file:{RAILWAY_NETWORK_PATH.as_posix()}?mode=ro"
    return sqlite3.connect(uri, uri=True)


def search_stations(keyword, limit=20):
    keyword = keyword.strip()[:80]
    if not keyword:
        return []

    like = f"%{keyword}%"
    upper = keyword.upper()
    with closing(connect_network()) as connection:
        rows = connection.execute(
            "SELECT name, code, city, pinyin, geom FROM railway_stations "
            "WHERE name LIKE ? OR code LIKE ? OR city LIKE ? OR pinyin LIKE ? "
            "ORDER BY CASE WHEN name = ? OR code = ? THEN 0 "
            "WHEN name LIKE ? OR code LIKE ? THEN 1 ELSE 2 END, name LIMIT ?",
            (like, f"%{upper}%", like, f"%{keyword.lower()}%", keyword, upper,
             f"{keyword}%", f"{upper}%", limit),
        ).fetchall()

    results = []
    for name, code, city, _pinyin, blob in rows:
        longitude, latitude = decode_geometry(blob).coords[0]
        results.append({
            "name": name,
            "code": code or "",
            "city": city or "",
            "longitude": longitude,
            "latitude": latitude,
        })
    return results
