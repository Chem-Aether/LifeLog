import sqlite3
import threading

from ..settings import POI_PATH


_local = threading.local()


def connection():
    database = getattr(_local, "database", None)
    if database is None:
        if not POI_PATH.is_file():
            raise FileNotFoundError(f"POI GeoPackage not found: {POI_PATH}")
        uri = f"file:{POI_PATH.as_posix()}?mode=ro"
        database = sqlite3.connect(uri, uri=True)
        database.row_factory = sqlite3.Row
        _local.database = database
    return database


def one(sql, parameters=()):
    row = connection().execute(sql, parameters).fetchone()
    return dict(row) if row else None


def all_rows(sql, parameters=()):
    return [dict(row) for row in connection().execute(sql, parameters).fetchall()]


def placeholders(values):
    return ",".join("?" for _ in values)
