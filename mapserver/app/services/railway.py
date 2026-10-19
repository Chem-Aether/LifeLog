import heapq
import itertools
import math
import struct
from collections import defaultdict
from contextlib import closing

from shapely import from_wkb

from ..integrations.spyderserver import get_train_map_line
from .railway_network import connect_network, connect_routing_network, decode_geometry


def _distance(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, (*a, *b))
    dlon, dlat = lon2 - lon1, lat2 - lat1
    haversine = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 12742017.6 * math.asin(math.sqrt(haversine))


def _station_key(name):
    value = "".join(str(name or "").split())
    return value[:-1] if value.endswith("站") else value


def _stations(connection):
    result = {}
    for blob, name, code in connection.execute("SELECT geom, name, code FROM railway_stations WHERE name IS NOT NULL"):
        item = {"name": name, "code": code or "", "coordinates": list(decode_geometry(blob).coords[0])}
        result.setdefault(_station_key(name), item)
    return result


def _build_graph(connection, bounds):
    if bounds and isinstance(bounds[0], (tuple, list)):
        values = ",".join("(?,?,?,?)" for _ in bounds)
        corridor = f"WITH corridor(minx,maxx,miny,maxy) AS (VALUES {values}), candidate_ids AS ("
        corridor += "SELECT DISTINCT bounds.id FROM rtree_railway_edges_geom AS bounds JOIN corridor "
        corridor += "ON bounds.minx<=corridor.maxx AND bounds.maxx>=corridor.minx "
        corridor += "AND bounds.miny<=corridor.maxy AND bounds.maxy>=corridor.miny) "
        query = corridor + """SELECT edge.fid, edge.osm_way_id, edge.distance_m, edge.name, edge.ref,
                      edge.usage, edge.service, edge.layer, edge.railway, edge.highspeed,
                      edge.from_way_endpoint, edge.to_way_endpoint,
                      edge.from_node, edge.to_node, edge.geom
               FROM railway_edges AS edge
               JOIN candidate_ids ON candidate_ids.id=edge.fid
               WHERE edge.railway IN ('rail', 'narrow_gauge')"""
        parameters = tuple(value for box in bounds for value in box)
    else:
        xmin, ymin, xmax, ymax = bounds
        query = """SELECT edge.fid, edge.osm_way_id, edge.distance_m, edge.name, edge.ref,
                       edge.usage, edge.service, edge.layer, edge.railway, edge.highspeed,
                       edge.from_way_endpoint, edge.to_way_endpoint,
                       edge.from_node, edge.to_node, edge.geom
                FROM railway_edges AS edge
                JOIN rtree_railway_edges_geom AS bounds ON bounds.id=edge.fid
                WHERE bounds.minx<=? AND bounds.maxx>=? AND bounds.miny<=? AND bounds.maxy>=?
                  AND edge.railway IN ('rail', 'narrow_gauge')"""
        parameters = (xmax, xmin, ymax, ymin)
    graph, coordinates, edges = defaultdict(list), {}, []
    endpoint_states = {}
    for row in connection.execute(query, parameters):
        (edge_id, way_id, length, name, ref, usage, service, layer, railway, highspeed,
         from_endpoint, to_endpoint, from_node, to_node, blob) = row
        wkb = blob[8:]
        byte_order = "<" if wkb and wkb[0] == 1 else ">"
        valid_header = len(wkb) >= 9 and wkb[0] in (0, 1)
        geometry_type = struct.unpack_from(f"{byte_order}I", wkb, 1)[0] if valid_header else None
        point_count = struct.unpack_from(f"{byte_order}I", wkb, 5)[0] if valid_header else 0
        if geometry_type == 2 and point_count >= 2 and len(wkb) >= 9 + point_count * 16:
            start_xy = struct.unpack_from(f"{byte_order}dd", wkb, 9)
            end_xy = struct.unpack_from(f"{byte_order}dd", wkb, 9 + (point_count - 1) * 16)
        else:
            line = from_wkb(wkb)
            start_xy, end_xy = tuple(line.coords[0][:2]), tuple(line.coords[-1][:2])
        start_state, end_state = (from_node, layer), (to_node, layer)
        coordinates[start_state], coordinates[end_state] = start_xy, end_xy
        edge = {"id": edge_id, "way": way_id, "length": float(length), "name": name or "", "ref": ref or "",
                "usage": usage or "", "service": service or "", "layer": layer, "railway": railway or "",
                "highspeed": highspeed or "", "a": start_state, "b": end_state,
                "a_xy": start_xy, "b_xy": end_xy}
        graph[start_state].append((end_state, edge))
        graph[end_state].append((start_state, edge))
        endpoint_states[(from_node, layer)] = endpoint_states.get((from_node, layer), False) or bool(from_endpoint)
        endpoint_states[(to_node, layer)] = endpoint_states.get((to_node, layer), False) or bool(to_endpoint)
        edges.append(edge)
    relation_groups, relation_names = defaultdict(set), defaultdict(set)
    way_ids = sorted({edge["way"] for edge in edges})
    for offset in range(0, len(way_ids), 400):
        batch = way_ids[offset:offset + 400]
        placeholders = ",".join("?" for _ in batch)
        relation_query = f"""SELECT member.member_id, relation.osm_relation_id,
                                  COALESCE(NULLIF(relation.ref,''),NULLIF(relation.name,''))
                           FROM railway_relation_members AS member
                           JOIN railway_relations AS relation USING(osm_relation_id)
                           WHERE member.member_type='w' AND member.member_id IN ({placeholders})
                             AND relation.route IN ('railway','train')"""
        for way_id, relation_id, relation_name in connection.execute(relation_query, batch):
            relation_groups[way_id].add(("relation", relation_id))
            if relation_name:
                relation_names[way_id].add(relation_name)
    for edge in edges:
        if relation_groups[edge["way"]]:
            edge["line_groups"] = frozenset(relation_groups[edge["way"]])
        elif edge["name"]:
            edge["line_groups"] = frozenset({("name", edge["name"].casefold())})
        elif edge["ref"]:
            edge["line_groups"] = frozenset({("ref", edge["ref"].casefold())})
        else:
            edge["line_groups"] = frozenset()
        edge["relationNames"] = sorted(relation_names[edge["way"]])
    layers_by_node = defaultdict(list)
    for (node_id, level), is_endpoint in endpoint_states.items():
        layers_by_node[node_id].append((level, is_endpoint))
    for node_id, layer_states in layers_by_node.items():
        for index, (first_level, first_endpoint) in enumerate(layer_states):
            for second_level, second_endpoint in layer_states[index + 1:]:
                if first_level == second_level or not (first_endpoint or second_endpoint):
                    continue
                first, second = (node_id, first_level), (node_id, second_level)
                transfer = {"way": None, "length": 0.0, "transfer": True, "id": None}
                graph[first].append((second, transfer))
                graph[second].append((first, transfer))
    return graph, coordinates, edges


def _project_to_segment(point, first, second):
    latitude_scale = 111_320.0
    longitude_scale = latitude_scale * math.cos(math.radians(point[1]))
    dx, dy = (second[0] - first[0]) * longitude_scale, (second[1] - first[1]) * latitude_scale
    px, py = (point[0] - first[0]) * longitude_scale, (point[1] - first[1]) * latitude_scale
    squared = dx * dx + dy * dy
    ratio = max(0.0, min(1.0, (px * dx + py * dy) / squared)) if squared else 0.0
    projected = (first[0] + (second[0] - first[0]) * ratio, first[1] + (second[1] - first[1]) * ratio)
    return projected, ratio, math.hypot(px - dx * ratio, py - dy * ratio)


def _snap(graph, coordinates, edges, station_coordinates, station_token, preferred_edge_id=None):
    if not edges:
        return None
    best = None
    candidates = edges if preferred_edge_id is None else (edge for edge in edges if edge["id"] == preferred_edge_id)
    for edge in candidates:
        projected, ratio, offset = _project_to_segment(station_coordinates, edge["a_xy"], edge["b_xy"])
        if best is None or (offset, edge["id"]) < (best[0], best[1]["id"]):
            best = (offset, edge, projected, ratio)
    if preferred_edge_id is not None and best is None:
        return None
    if best is None or best[0] > 5000:
        return None
    offset, edge, projected, ratio = best
    virtual = ("station", station_token, edge["layer"])
    coordinates[virtual] = projected
    graph[virtual] = []
    for node, weight in ((edge["a"], edge["length"] * ratio), (edge["b"], edge["length"] * (1 - ratio))):
        connector = {"id": edge["id"], "way": edge["way"], "length": max(0.0, weight) + offset, "usage": edge["usage"],
                     "highspeed": edge["highspeed"], "name": edge["name"], "ref": edge["ref"], "layer": edge["layer"],
                     "railway": edge["railway"], "service": edge["service"],
                     "line_groups": edge.get("line_groups", frozenset()), "a": virtual, "b": node}
        graph[virtual].append((node, connector))
        graph[node].append((virtual, connector))
    return virtual


def _shortest_path(graph, start, end, branch_penalty, switch_penalty, speed_preference=False, coordinates=None):
    initial = (start, frozenset())
    sequence = itertools.count()
    queue, distances, previous = [(0.0, 0.0, next(sequence), initial)], {initial: 0.0}, {}
    heuristic_by_node = {}
    if coordinates is not None:
        end_lon, end_lat = coordinates[end]
        x_scale = 111_000 * math.cos(math.radians(end_lat))
        heuristic_by_node = {
            node: 0.85 * math.hypot((point[0] - end_lon) * x_scale, (point[1] - end_lat) * 111_000)
            for node, point in coordinates.items()
        }
    target = None
    while queue:
        _priority, cost, _order, state = heapq.heappop(queue)
        if cost != distances[state]:
            continue
        node, previous_groups = state
        if node == end:
            target = state
            break
        for neighbor, edge in graph.get(node, ()):
            edge_cost = edge["length"]
            if edge.get("transfer"):
                edge_cost = 0.0
            elif edge.get("usage") == "branch":
                edge_cost *= 1 + branch_penalty
            if speed_preference and edge.get("railway") not in {"rail", "light_rail"}:
                edge_cost *= 1.25
            edge_groups = edge.get("line_groups", frozenset())
            if not edge.get("transfer") and previous_groups and edge_groups and previous_groups.isdisjoint(edge_groups):
                edge_cost += switch_penalty
            next_groups = previous_groups if edge.get("transfer") or not edge_groups else edge_groups
            next_state = (neighbor, next_groups)
            candidate = cost + edge_cost
            if candidate < distances.get(next_state, float("inf")):
                distances[next_state] = candidate
                previous[next_state] = (state, edge)
                heuristic = heuristic_by_node.get(neighbor, 0.0)
                heapq.heappush(queue, (candidate + heuristic, candidate, next(sequence), next_state))
    if target is None:
        return None
    states, path_edges = [target], []
    while states[-1] != initial:
        parent, edge = previous[states[-1]]
        path_edges.append(edge)
        states.append(parent)
    states.reverse()
    path_edges.reverse()
    return [state[0] for state in states], path_edges


def _route_corridor(first_xy, second_xy, width_km):
    distance = _distance(first_xy, second_xy)
    steps = max(1, math.ceil(distance / 50_000))
    boxes = []
    for index in range(steps + 1):
        ratio = index / steps
        longitude = first_xy[0] + (second_xy[0] - first_xy[0]) * ratio
        latitude = first_xy[1] + (second_xy[1] - first_xy[1]) * ratio
        lat_padding = width_km / 111.32
        lon_padding = width_km / (111.32 * max(0.2, math.cos(math.radians(latitude))))
        boxes.append((longitude - lon_padding, longitude + lon_padding,
                      latitude - lat_padding, latitude + lat_padding))
    return boxes


def _route_candidates(connection, first, second, index, station_anchors):
    first_xy, second_xy = first["coordinates"], second["coordinates"]
    padding = max(0.35, min(2.0, _distance(first_xy, second_xy) / 450_000))
    bounds = (min(first_xy[0], second_xy[0]) - padding, min(first_xy[1], second_xy[1]) - padding,
              max(first_xy[0], second_xy[0]) + padding, max(first_xy[1], second_xy[1]) + padding)
    modes = [
        ("线路连续优先", 0.45, 18_000.0, False),
        ("物理距离较短", 0.0, 0.0, False),
        ("正线优先", 1.25, 35_000.0, True),
    ]
    candidates, seen = [], set()
    for corridor in (_route_corridor(first_xy, second_xy, 50),
                     _route_corridor(first_xy, second_xy, 120),
                     _route_corridor(first_xy, second_xy, 240), bounds):
        graph, coordinates, edges = _build_graph(connection, corridor)
        start = _snap(graph, coordinates, edges, first_xy, f"{index}-start", station_anchors.get(index))
        end = _snap(graph, coordinates, edges, second_xy, f"{index}-end", station_anchors.get(index + 1))
        if start is None or end is None:
            continue
        for mode_index, (label, branch_penalty, switch_penalty, speed_preference) in enumerate(modes):
            result = _shortest_path(graph, start, end, branch_penalty, switch_penalty, speed_preference,
                                    coordinates=coordinates)
            if not result:
                continue
            path, path_edges = result
            signature = tuple(edge["id"] for edge in path_edges if edge.get("id") is not None)
            if not signature or signature in seen:
                continue
            seen.add(signature)
            path_coordinates = [coordinates[node] for node in path]
            # Keep the route on the snapped rail geometry; station centers are display anchors only.
            route_coordinates = []
            for coordinate in path_coordinates:
                if not route_coordinates or coordinate != route_coordinates[-1]:
                    route_coordinates.append(coordinate)
            distance = sum(_distance(a, b) for a, b in zip(route_coordinates, route_coordinates[1:]))
            line_lengths = defaultdict(float)
            for edge in path_edges:
                if edge.get("transfer"):
                    continue
                name = edge["name"] or (edge.get("relationNames") or [None])[0] or edge["ref"]
                if name:
                    line_lengths[name] += edge["length"]
            line_names = sorted(line_lengths, key=line_lengths.get, reverse=True)
            candidates.append({
                "sourceLayer": f"osm-route-{mode_index}", "label": label,
                "coordinates": route_coordinates, "distanceKm": round(distance / 1000, 1),
                "railwayLines": line_names,
            })
        if candidates:
            station_anchors.setdefault(index, graph[start][0][1]["id"])
            station_anchors.setdefault(index + 1, graph[end][0][1]["id"])
            return candidates
    return candidates


def _sample_train_map_line(station_names, train_map):
    geometry = train_map["geometry"]
    try:
        with closing(connect_network()) as connection:
            station_index = _stations(connection)
    except FileNotFoundError:
        return None

    matched, unmatched = [], []
    for order, requested_name in enumerate(station_names, 1):
        station = station_index.get(_station_key(requested_name))
        if station:
            matched.append({**station, "requestedName": requested_name, "order": order})
        else:
            unmatched.append(requested_name)
    if len(matched) < 2:
        return None

    stations = [{
        "stationName": item["name"],
        "stationCode": item["code"],
        "longitude": item["coordinates"][0],
        "latitude": item["coordinates"][1],
        "sequence": index + 1,
    } for index, item in enumerate(matched)]
    return {
        "geometry": geometry,
        "stations": stations,
        "routeSegments": [],
        "unmatchedStations": unmatched,
        "fallbackSegments": [],
        "source": "12306-map-line",
    }


def sample_route(station_names, train_code=None):
    if train_code:
        train_map = get_train_map_line(train_code)
        if train_map:
            result = _sample_train_map_line(station_names, train_map)
            if result:
                return result
    return _sample_network_route(station_names)


def _sample_network_route(station_names):
    try:
        with closing(connect_network()) as station_connection:
            station_index = _stations(station_connection)
        with closing(connect_routing_network()) as routing_connection:
            matched, unmatched = [], []
            for order, requested_name in enumerate(station_names, 1):
                station = station_index.get(_station_key(requested_name))
                if station:
                    matched.append({**station, "requestedName": requested_name, "order": order})
                else:
                    unmatched.append(requested_name)
            if len(matched) < 2:
                raise ValueError(f"铁路网只匹配到 {len(matched)} 个车站，无法生成轨迹")

            route_segments, segments, fallback_segments = [], [], []
            station_anchors = {}
            for segment_index, (first, second) in enumerate(zip(matched, matched[1:])):
                candidates = _route_candidates(routing_connection, first, second, segment_index, station_anchors)
                if candidates:
                    segment = candidates[0]["coordinates"]
                    used_layer = candidates[0]["sourceLayer"]
                else:
                    segment = [first["coordinates"], second["coordinates"]]
                    used_layer = "straight-fallback"
                    fallback_segments.append({"from": first["name"], "to": second["name"]})
                segments.append({"coordinates": segment, "layer": used_layer})
                route_segments.append({"fromStation": first["name"], "toStation": second["name"],
                                       "defaultCoordinates": segment, "candidates": candidates,
                                       "selectedCandidate": 0 if candidates else None})
    except FileNotFoundError as exc:
        raise ValueError(str(exc)) from exc

    geometry = {"type": "MultiLineString", "coordinates": [segment["coordinates"] for segment in segments]}
    stations = [{"stationName": item["name"], "stationCode": item["code"], "longitude": item["coordinates"][0],
                 "latitude": item["coordinates"][1], "sequence": index + 1} for index, item in enumerate(matched)]
    return {"geometry": geometry, "stations": stations, "routeSegments": route_segments,
            "unmatchedStations": unmatched, "fallbackSegments": fallback_segments,
            "source": "railway-network"}
