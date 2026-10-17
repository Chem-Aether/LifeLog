from shapely.geometry import LineString

from app.services import railway


class _LineConnection:
    def __init__(self, rows):
        self.rows = rows

    def execute(self, _query, _parameters):
        return self.rows


def _build_crossing_graph(monkeypatch, crossing_levels):
    geometries = {
        b"horizontal": LineString([(0, 0), (1, 1), (2, 2)]),
        b"vertical": LineString([(0, 2), (1, 1), (2, 0)]),
    }
    monkeypatch.setattr(railway, "decode_geometry", geometries.__getitem__)
    connection = _LineConnection([
        (b"horizontal", f'"layer"=>"{crossing_levels[0]}"'),
        (b"vertical", f'"layer"=>"{crossing_levels[1]}"'),
    ])
    return railway._build_graph(connection, "rail_lines", (-1, -1, 3, 3))[0]


def test_grade_separated_crossing_does_not_connect(monkeypatch):
    graph = _build_crossing_graph(monkeypatch, (0, 1))

    assert railway._shortest_path(graph, (0, 0, 0.0), (2, 0, 1.0)) is None


def test_bridge_and_tunnel_tags_supply_missing_layer():
    assert railway._track_level('"bridge"=>"yes"') == 1.0
    assert railway._track_level('"tunnel"=>"yes"') == -1.0
    assert railway._track_level('"bridge"=>"yes","layer"=>"2"') == 2.0


def test_same_level_railway_intersection_connects(monkeypatch):
    graph = _build_crossing_graph(monkeypatch, (0, 0))

    path = railway._shortest_path(graph, (0, 0, 0.0), (2, 0, 0.0))
    assert path is not None
    assert (1, 1, 0.0) in path


def test_grade_change_connects_at_shared_track_endpoints(monkeypatch):
    geometries = {
        b"ground": LineString([(0, 0), (1, 0)]),
        b"bridge": LineString([(1, 0), (2, 0)]),
    }
    monkeypatch.setattr(railway, "decode_geometry", geometries.__getitem__)
    connection = _LineConnection([
        (b"ground", '"layer"=>"0"'),
        (b"bridge", '"layer"=>"1"'),
    ])
    graph, _ = railway._build_graph(connection, "rail_lines", (-1, -1, 3, 1))

    path = railway._shortest_path(graph, (0, 0, 0.0), (2, 0, 1.0))
    assert path == [(0, 0, 0.0), (1, 0, 0.0), (1, 0, 1.0), (2, 0, 1.0)]


