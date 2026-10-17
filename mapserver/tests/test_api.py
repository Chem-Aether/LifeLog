from fastapi.testclient import TestClient
import math

import mapbox_vector_tile

from main import app
from app.services import admin_boundaries


client = TestClient(app)


def test_reverse_geocode_uses_matched_node_ancestors(monkeypatch):
    import sqlite3
    from shapely.geometry import box

    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    connection.executescript("""
        CREATE TABLE admin_boundaries (
            fid INTEGER PRIMARY KEY, node_id TEXT, parent_id TEXT, country_code TEXT,
            region_code TEXT, level INTEGER, name TEXT, type TEXT, geom BLOB,
            bbox_area REAL, data_version TEXT
        );
        CREATE TABLE rtree_admin_boundaries_geom (
            id INTEGER, minx REAL, maxx REAL, miny REAL, maxy REAL
        );
    """)
    geometries = {
        b"province": box(0, 0, 1, 1),
        b"city": box(0, 0, 1, 1),
        b"district": box(10, 10, 20, 20),
        b"municipality": box(0, 0, 1, 1),
        b"municipal_city": box(0, 0, 1, 1),
        b"municipal_district": box(25, 25, 30, 30),
    }
    rows = [
        (1, "CN:0:CN", None, "CN", None, 0, "中国", "country", b"province", 1, "test"),
        (2, "CN:1:32", "CN:0:CN", "CN", "32", 1, "江苏省", "省级", b"province", 1, "test"),
        (3, "CN:2:3201", "CN:1:32", "CN", "3201", 2, "南京市", "市级", b"city", 1, "test"),
        (4, "CN:3:320102", "CN:2:3201", "CN", "320102", 3, "玄武区", "区县级", b"district", 1, "test"),
        (5, "CN:1:11", "CN:0:CN", "CN", "11", 1, "北京市", "省级", b"municipality", 1, "test"),
        (6, "CN:2:11", "CN:1:11", "CN", "11", 2, "北京市", "市级", b"municipal_city", 1, "test"),
        (7, "CN:3:110101", "CN:2:11", "CN", "110101", 3, "东城区", "区县级", b"municipal_district", 1, "test"),
    ]
    connection.executemany("INSERT INTO admin_boundaries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
    connection.execute("INSERT INTO rtree_admin_boundaries_geom VALUES (4, 10, 20, 10, 20)")
    connection.execute("INSERT INTO rtree_admin_boundaries_geom VALUES (7, 25, 30, 25, 30)")

    class ReusableConnection:
        def execute(self, *args):
            return connection.execute(*args)

        def close(self):
            pass

    monkeypatch.setattr(admin_boundaries, "_connect", ReusableConnection)
    monkeypatch.setattr(admin_boundaries, "decode_geometry", lambda geometry: geometries[geometry])
    admin_boundaries._reverse_cached.cache_clear()

    result = admin_boundaries.reverse(15, 15)

    assert [node["id"] for node in result["path"]] == ["CN:0:CN", "CN:1:32", "CN:2:3201", "CN:3:320102"]
    assert result["path"][-1]["regionCode"] == "320102"
    assert [node["name"] for node in result["path"]] == ["中国", "江苏省", "南京市", "玄武区"]

    municipality = admin_boundaries.reverse(27, 27)
    assert [node["id"] for node in municipality["path"]] == ["CN:0:CN", "CN:1:11", "CN:2:11", "CN:3:110101"]
    admin_boundaries._reverse_cached.cache_clear()
    connection.close()


def test_catalog_search_is_keyword_driven_and_limited():
    empty_airports = client.get("/api/airports/search")
    assert empty_airports.status_code == 200
    assert empty_airports.json()["data"] == []
    airports = client.get("/api/airports/search", params={"keyword": "PEK"})
    assert airports.status_code == 200
    assert airports.json()["data"]
    assert airports.json()["data"][0]["icao"] == "ZBAA"
    assert airports.json()["data"][0]["longitude"] > 0

    by_code = client.post("/api/airports/by-codes", json={"codes": ["PEK"]})
    assert by_code.status_code == 200
    assert by_code.json()["data"][0]["icao"] == "ZBAA"

    stations = client.get("/api/railway/stations/search", params={"keyword": "南京", "limit": 1})
    assert stations.status_code == 200
    assert len(stations.json()["data"]) <= 1
    if stations.json()["data"]:
        station = stations.json()["data"][0]
        assert station["name"]
        assert -180 <= station["longitude"] <= 180
        assert -90 <= station["latitude"] <= 90

    invalid_limit = client.get("/api/airports/search", params={"keyword": "北京", "limit": 51})
    assert invalid_limit.status_code == 422


def test_region_queries_and_boundaries():
    region = client.get("/api/regions/CN:3:320102")
    assert region.status_code == 200
    assert [node["id"] for node in region.json()["data"]["path"]] == [
        "CN:0:CN", "CN:1:32", "CN:2:3201", "CN:3:320102"
    ]
    assert "fullName" not in region.json()["data"]

    region_search = client.get("/api/regions/search", params={"keyword": "南京", "level": 2})
    assert region_search.status_code == 200
    assert region_search.json()["data"]
    assert region_search.json()["data"][0]["path"][-1]["level"] == 2

    reverse = client.get("/api/regions/reverse", params={"longitude": 118.8489, "latitude": 32.0416})
    assert reverse.status_code == 200
    assert reverse.json()["data"]["path"][-1]["id"] == "CN:3:320102"

    batch = client.post("/api/regions/reverse/batch", json={"points": [
        {"id": "footprint:1", "longitude": 118.8489, "latitude": 32.0416}
    ]})
    assert batch.status_code == 200
    assert batch.json()["data"][0]["id"] == "footprint:1"

    paths = client.post("/api/regions/paths", json={"ids": ["CN:3:320102", "CN:3:320102"]})
    assert paths.status_code == 200
    assert [node["id"] for node in paths.json()["data"]["CN:3:320102"]["path"]] == [
        "CN:0:CN", "CN:1:32", "CN:2:3201", "CN:3:320102"
    ]

    poi = client.get("/api/pois/search", params={"q": "中山陵", "limit": 2})
    assert poi.status_code == 200
    assert poi.json()["data"][0]["nameZh"] == "中山陵"
    assert poi.json()["data"][0]["longitude"]

    partial = client.get("/api/pois/search", params={"q": "中山", "limit": 5})
    assert partial.status_code == 200
    assert any("中山" in item["name"] for item in partial.json()["data"])

    multiple_terms = client.get("/api/pois/search", params={"q": "南京 博物院", "limit": 5})
    assert multiple_terms.status_code == 200
    assert any("博物" in item["name"] for item in multiple_terms.json()["data"])

    boundary = client.post("/api/regions/boundaries", json={"ids": ["CN:2:3201"]})
    assert boundary.status_code == 200
    assert boundary.headers["content-type"].startswith("application/geo+json")
    assert boundary.json()["type"] == "FeatureCollection"


def test_railway_route_sampling_uses_local_network():
    stations = client.get("/api/railway/stations/search", params={"keyword": "南京南", "limit": 5})
    assert stations.status_code == 200
    assert stations.json()["data"]

    response = client.post("/api/railway/routes/sample", json={
        "trainCode": "G1", "stations": ["南京南", "南京"]
    })
    assert response.status_code == 200
    result = response.json()["data"]
    assert result["geometry"]["type"] == "MultiLineString"
    assert len(result["geometry"]["coordinates"][0]) > 2
    assert len(result["routeSegments"]) == 1
    assert result["routeSegments"][0]["candidates"]
    assert len(result["routeSegments"][0]["defaultCoordinates"]) > 2
    assert [station["stationName"] for station in result["stations"]] == ["南京南", "南京"]
    assert result["unmatchedStations"] == []


def _tile_at(longitude, latitude, zoom):
    x = int((longitude + 180) / 360 * (1 << zoom))
    y = int((1 - math.asinh(math.tan(math.radians(latitude))) / math.pi) / 2 * (1 << zoom))
    return zoom, x, y


def test_unified_tile_gateway_selects_sources_and_overzooms():
    tilejson = client.get("/api/tiles/tilejson.json")
    assert tilejson.status_code == 200
    assert tilejson.json()["tiles"][0].endswith("/api/tiles/{z}/{x}/{y}.mvt")

    world = client.get("/api/tiles/0/0/0.mvt")
    assert world.status_code == 200
    assert world.headers["x-map-source"] == "world"
    assert world.headers["x-map-overzoom"] == "false"

    z, x, y = _tile_at(118.8, 32.0, 14)
    china = client.get(f"/api/tiles/{z}/{x}/{y}.mvt")
    assert china.status_code == 200
    assert china.headers["x-map-source"] == "china"

    z, x, y = _tile_at(118.8, 32.0, 15)
    overzoom = client.get(f"/api/tiles/{z}/{x}/{y}.mvt")
    assert overzoom.status_code == 200
    assert overzoom.headers["x-map-source"] == "china"
    assert overzoom.headers["x-map-overzoom"] == "true"
    decoded = mapbox_vector_tile.decode(overzoom.content)
    assert isinstance(decoded, dict)
