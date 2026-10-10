import heapq
import math
from collections import defaultdict
from contextlib import closing

from .railway_network import connect_network, decode_geometry


def _distance(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, (*a, *b))
    delta_lon, delta_lat = lon2 - lon1, lat2 - lat1
    value = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    return 12742017.6 * math.asin(math.sqrt(value))


def _station_key(name):
    value = "".join(str(name or "").split())
    return value[:-1] if value.endswith("站") else value


def _stations(connection):
    result = {}
    for blob, name, code in connection.execute("SELECT geom, name, code FROM railway_stations WHERE name IS NOT NULL"):
        item = {"name": name, "code": code or "", "coordinates": list(decode_geometry(blob).coords[0])}
        result.setdefault(_station_key(name), item)
    return result


def _build_graph(connection, layer, bounds):
    xmin, ymin, xmax, ymax = bounds
    index = f"rtree_{layer}_geom"
    query = f"SELECT geom FROM {layer} WHERE fid IN (SELECT id FROM {index} WHERE minx<=? AND maxx>=? AND miny<=? AND maxy>=?)"
    graph, grid = defaultdict(dict), defaultdict(list)
    for (blob,) in connection.execute(query, (xmax, xmin, ymax, ymin)):
        geometry = decode_geometry(blob)
        parts = [geometry] if geometry.geom_type == "LineString" else list(geometry.geoms)
        for part in parts:
            points = [(round(point[0], 6), round(point[1], 6)) for point in part.coords]
            for first, second in zip(points, points[1:]):
                if first == second:
                    continue
                weight = _distance(first, second)
                graph[first][second] = min(weight, graph[first].get(second, float("inf")))
                graph[second][first] = min(weight, graph[second].get(first, float("inf")))
            for point in points:
                grid[(math.floor(point[0] / .02), math.floor(point[1] / .02))].append(point)
    return graph, grid


def _snap(grid, coordinates):
    gx, gy = math.floor(coordinates[0] / .02), math.floor(coordinates[1] / .02)
    candidates = {point for x in range(gx - 4, gx + 5) for y in range(gy - 4, gy + 5) for point in grid.get((x, y), ())}
    if not candidates:
        return None
    closest = min(candidates, key=lambda point: _distance(coordinates, point))
    return closest if _distance(coordinates, closest) <= 3000 else None


def _shortest_path(graph, start, end):
    queue, distances, previous = [(0.0, start)], {start: 0.0}, {}
    while queue:
        cost, node = heapq.heappop(queue)
        if node == end:
            path = [node]
            while node in previous:
                node = previous[node]
                path.append(node)
            return path[::-1]
        if cost != distances[node]:
            continue
        for neighbor, weight in graph[node].items():
            candidate = cost + weight
            if candidate < distances.get(neighbor, float("inf")):
                distances[neighbor] = candidate
                previous[neighbor] = node
                heapq.heappush(queue, (candidate, neighbor))
    return None


def sample_route(train_code, station_names):
    try:
        connection = connect_network()
    except FileNotFoundError as exc:
        raise ValueError("铁路网 GeoPackage 不存在") from exc
    with closing(connection):
        station_index = _stations(connection)
        matched, unmatched = [], []
        for order, requested_name in enumerate(station_names, 1):
            station = station_index.get(_station_key(requested_name))
            if station:
                matched.append({**station, "requestedName": requested_name, "order": order})
            else:
                unmatched.append(requested_name)
        if len(matched) < 2:
            raise ValueError(f"铁路网只匹配到 {len(matched)} 个车站，无法生成轨迹")

        preferred = "hsr_intercity_lines" if train_code.upper().startswith(("G", "C")) else "all_rail_lines"
        segments, fallback_segments = [], []
        for first, second in zip(matched, matched[1:]):
            padding = 1.0
            first_xy, second_xy = first["coordinates"], second["coordinates"]
            bounds = (min(first_xy[0], second_xy[0]) - padding, min(first_xy[1], second_xy[1]) - padding,
                      max(first_xy[0], second_xy[0]) + padding, max(first_xy[1], second_xy[1]) + padding)
            graphs = {}
            path = None
            used_layer = preferred
            for layer in dict.fromkeys((preferred, "all_rail_lines")):
                if layer not in graphs:
                    graphs[layer] = _build_graph(connection, layer, bounds)
                graph, grid = graphs[layer]
                start, end = _snap(grid, first["coordinates"]), _snap(grid, second["coordinates"])
                path = _shortest_path(graph, start, end) if start and end else None
                if path:
                    used_layer = layer
                    break
            if path:
                segment = [first["coordinates"], *path, second["coordinates"]]
            else:
                segment = [first["coordinates"], second["coordinates"]]
                fallback_segments.append({"from": first["name"], "to": second["name"]})
            segments.append({"coordinates": segment, "layer": used_layer})

    geometry = {"type": "MultiLineString", "coordinates": [segment["coordinates"] for segment in segments]}
    stations = [{"stationName": item["name"], "stationCode": item["code"], "longitude": item["coordinates"][0],
                 "latitude": item["coordinates"][1], "sequence": index + 1} for index, item in enumerate(matched)]
    return {"trainCode": train_code, "geometry": geometry, "stations": stations, "unmatchedStations": unmatched,
            "fallbackSegments": fallback_segments}
