package railway

import (
	"container/heap"
	"database/sql"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"net/url"
	"sort"
	"strings"

	"github.com/paulmach/orb"
	"lifelog/goserver/internal/platform/geospatial"
)

type station struct {
	Name, Code string
	Point      orb.Point
}
type graphNode struct {
	OSM   int64
	Layer int
}
type edge struct {
	ID, Way                   int64
	Length                    float64
	Name, Ref, Usage, Railway string
	RelationName              string
	A, B                      graphNode
	Coordinates               orb.LineString
	Groups                    string
	FromEndpoint, ToEndpoint  bool
}
type arc struct {
	To   graphNode
	Edge *edge
	Cost float64
}
type pathState struct {
	Node  graphNode
	Group string
}
type queueItem struct {
	State          pathState
	Cost, Priority float64
	Index          int
}
type priorityQueue []*queueItem

func (q priorityQueue) Len() int           { return len(q) }
func (q priorityQueue) Less(i, j int) bool { return q[i].Priority < q[j].Priority }
func (q priorityQueue) Swap(i, j int)      { q[i], q[j] = q[j], q[i]; q[i].Index = i; q[j].Index = j }
func (q *priorityQueue) Push(x any) {
	item := x.(*queueItem)
	item.Index = len(*q)
	*q = append(*q, item)
}
func (q *priorityQueue) Pop() any {
	old := *q
	n := len(old)
	item := old[n-1]
	*q = old[:n-1]
	return item
}

func (h *Handler) Sample(names []string, trainCode string) (map[string]any, error) {
	stations, unmatched, err := h.matchStations(names)
	if err != nil {
		return nil, err
	}
	if len(stations) < 2 {
		return nil, fmt.Errorf("铁路网只匹配到 %d 个车站，无法生成轨迹", len(stations))
	}
	if strings.TrimSpace(trainCode) != "" {
		if result := h.trainLine(trainCode, names, stations, unmatched); result != nil {
			return result, nil
		}
	}
	segments := make([]map[string]any, 0, len(stations)-1)
	lines := make([]any, 0, len(stations)-1)
	fallbacks := make([]map[string]string, 0)
	for i := 0; i < len(stations)-1; i++ {
		candidates, err := h.routeCandidates(stations[i], stations[i+1], i)
		if err != nil {
			return nil, err
		}
		coords := [][]float64{{stations[i].Point[0], stations[i].Point[1]}, {stations[i+1].Point[0], stations[i+1].Point[1]}}
		selected := any(nil)
		layer := "straight-fallback"
		if len(candidates) > 0 {
			coords = candidates[0]["coordinates"].([][]float64)
			selected = 0
			layer = candidates[0]["sourceLayer"].(string)
		} else {
			fallbacks = append(fallbacks, map[string]string{"from": stations[i].Name, "to": stations[i+1].Name})
		}
		segments = append(segments, map[string]any{"fromStation": stations[i].Name, "toStation": stations[i+1].Name, "defaultCoordinates": coords, "candidates": candidates, "selectedCandidate": selected})
		lines = append(lines, map[string]any{"coordinates": coords, "layer": layer})
	}
	stationRows := make([]map[string]any, 0, len(stations))
	for i, s := range stations {
		stationRows = append(stationRows, map[string]any{"stationName": s.Name, "stationCode": s.Code, "longitude": s.Point[0], "latitude": s.Point[1], "sequence": i + 1})
	}
	coordinates := make([]any, 0, len(lines))
	for _, segment := range lines {
		coordinates = append(coordinates, segment.(map[string]any)["coordinates"])
	}
	return map[string]any{"geometry": map[string]any{"type": "MultiLineString", "coordinates": coordinates}, "stations": stationRows, "routeSegments": segments, "unmatchedStations": unmatched, "fallbackSegments": fallbacks, "source": "railway-network"}, nil
}

func stationKey(s string) string {
	s = strings.Join(strings.Fields(s), "")
	return strings.TrimSuffix(s, "站")
}
func (h *Handler) matchStations(names []string) ([]station, []string, error) {
	rows, err := h.network.Query(`SELECT name,code,geom FROM railway_stations WHERE name IS NOT NULL`)
	if err != nil {
		return nil, nil, err
	}
	defer rows.Close()
	index := map[string]station{}
	for rows.Next() {
		var name string
		var code sql.NullString
		var blob []byte
		if err := rows.Scan(&name, &code, &blob); err != nil {
			return nil, nil, err
		}
		g, err := geospatial.DecodeGeoPackage(blob)
		if err != nil {
			continue
		}
		point, ok := g.(orb.Point)
		if ok {
			index[stationKey(name)] = station{name, value(code), point}
		}
	}
	if err := rows.Err(); err != nil {
		return nil, nil, err
	}
	matched := make([]station, 0, len(names))
	unmatched := make([]string, 0)
	for _, name := range names {
		if item, ok := index[stationKey(name)]; ok {
			matched = append(matched, item)
		} else {
			unmatched = append(unmatched, name)
		}
	}
	return matched, unmatched, nil
}

func (h *Handler) trainLine(code string, names []string, stations []station, unmatched []string) map[string]any {
	request, err := http.NewRequest(http.MethodGet, h.spiderURL+"/trains/"+url.PathEscape(code)+"/map-line", nil)
	if err != nil {
		return nil
	}
	response, err := h.client.Do(request)
	if err != nil {
		return nil
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return nil
	}
	var body struct {
		Data struct {
			Geometry json.RawMessage `json:"geometry"`
		} `json:"data"`
	}
	if json.NewDecoder(response.Body).Decode(&body) != nil {
		return nil
	}
	var geometry struct {
		Type        string      `json:"type"`
		Coordinates [][]float64 `json:"coordinates"`
	}
	if json.Unmarshal(body.Data.Geometry, &geometry) != nil || geometry.Type != "LineString" || len(geometry.Coordinates) < 2 {
		return nil
	}
	stationRows := make([]map[string]any, 0, len(stations))
	for i, s := range stations {
		stationRows = append(stationRows, map[string]any{"stationName": s.Name, "stationCode": s.Code, "longitude": s.Point[0], "latitude": s.Point[1], "sequence": i + 1})
	}
	return map[string]any{"geometry": geometry, "stations": stationRows, "routeSegments": []any{}, "unmatchedStations": unmatched, "fallbackSegments": []any{}, "source": "12306-map-line"}
}

func (h *Handler) routeCandidates(first, second station, index int) ([]map[string]any, error) {
	padding := math.Max(.35, math.Min(2, haversine(first.Point, second.Point)/450000))
	bounds := []float64{math.Min(first.Point[0], second.Point[0]) - padding, math.Min(first.Point[1], second.Point[1]) - padding, math.Max(first.Point[0], second.Point[0]) + padding, math.Max(first.Point[1], second.Point[1]) + padding}
	rows, err := h.routing.Query(`SELECT e.fid,e.osm_way_id,e.distance_m,COALESCE(e.name,''),COALESCE(e.ref,''),COALESCE(e.usage,''),COALESCE(e.layer,0),COALESCE(e.railway,''),e.from_node,e.to_node,e.from_way_endpoint,e.to_way_endpoint,e.geom FROM railway_edges e JOIN rtree_railway_edges_geom r ON r.id=e.fid WHERE r.minx<=? AND r.maxx>=? AND r.miny<=? AND r.maxy>=? AND e.railway IN ('rail','narrow_gauge')`, bounds[2], bounds[0], bounds[3], bounds[1])
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	graph := map[graphNode][]arc{}
	coords := map[graphNode]orb.Point{}
	edges := make([]*edge, 0)
	for rows.Next() {
		var e edge
		var layer int
		var fromEndpoint, toEndpoint int
		var blob []byte
		if err := rows.Scan(&e.ID, &e.Way, &e.Length, &e.Name, &e.Ref, &e.Usage, &layer, &e.Railway, &e.A.OSM, &e.B.OSM, &fromEndpoint, &toEndpoint, &blob); err != nil {
			return nil, err
		}
		e.A.Layer, e.B.Layer = layer, layer
		e.FromEndpoint, e.ToEndpoint = fromEndpoint != 0, toEndpoint != 0
		e.Groups = e.Name
		if e.Groups == "" {
			e.Groups = e.Ref
		}
		g, err := geospatial.DecodeGeoPackage(blob)
		if err != nil {
			continue
		}
		line, ok := g.(orb.LineString)
		if !ok || len(line) < 2 {
			continue
		}
		e.Coordinates = line
		coords[e.A], coords[e.B] = line[0], line[len(line)-1]
		edges = append(edges, &e)
		graph[e.A] = append(graph[e.A], arc{e.B, &e, e.Length})
		graph[e.B] = append(graph[e.B], arc{e.A, &e, e.Length})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	wayIDs := make([]int64, 0, len(edges))
	seenWays := map[int64]bool{}
	for _, e := range edges {
		if !seenWays[e.Way] {
			seenWays[e.Way] = true
			wayIDs = append(wayIDs, e.Way)
		}
	}
	for start := 0; start < len(wayIDs); start += 400 {
		end := min(start+400, len(wayIDs))
		marks := strings.TrimRight(strings.Repeat("?,", end-start), ",")
		args := make([]any, 0, end-start)
		for _, id := range wayIDs[start:end] {
			args = append(args, id)
		}
		relationRows, err := h.routing.Query(`SELECT m.member_id,r.osm_relation_id,COALESCE(NULLIF(r.ref,''),NULLIF(r.name,''),'') FROM railway_relation_members m JOIN railway_relations r USING(osm_relation_id) WHERE m.member_type='w' AND m.member_id IN (`+marks+`) AND r.route IN ('railway','train')`, args...)
		if err != nil {
			return nil, err
		}
		groups := map[int64]string{}
		relationNames := map[int64]string{}
		for relationRows.Next() {
			var way, relation int64
			var name string
			if err := relationRows.Scan(&way, &relation, &name); err != nil {
				relationRows.Close()
				return nil, err
			}
			groups[way] = fmt.Sprintf("rel:%d", relation)
			if name != "" {
				relationNames[way] = name
			}
		}
		if err := relationRows.Err(); err != nil {
			relationRows.Close()
			return nil, err
		}
		relationRows.Close()
		for _, e := range edges {
			if group := groups[e.Way]; group != "" {
				e.Groups = group
			}
			e.RelationName = relationNames[e.Way]
		}
	}
	endpointLayers := map[int64]map[int]bool{}
	for _, e := range edges {
		if e.FromEndpoint {
			if endpointLayers[e.A.OSM] == nil {
				endpointLayers[e.A.OSM] = map[int]bool{}
			}
			endpointLayers[e.A.OSM][e.A.Layer] = true
		}
		if e.ToEndpoint {
			if endpointLayers[e.B.OSM] == nil {
				endpointLayers[e.B.OSM] = map[int]bool{}
			}
			endpointLayers[e.B.OSM][e.B.Layer] = true
		}
	}
	for node, layers := range endpointLayers {
		list := make([]int, 0, len(layers))
		for layer := range layers {
			list = append(list, layer)
		}
		for i := range list {
			for j := i + 1; j < len(list); j++ {
				a, b := graphNode{node, list[i]}, graphNode{node, list[j]}
				graph[a] = append(graph[a], arc{To: b, Cost: 0})
				graph[b] = append(graph[b], arc{To: a, Cost: 0})
			}
		}
	}
	start, sc, ok1 := snap(first.Point, edges, -1)
	end, ec, ok2 := snap(second.Point, edges, -2)
	if !ok1 || !ok2 {
		return nil, nil
	}
	coords[start] = sc
	coords[end] = ec
	for _, connector := range []struct {
		virtual graphNode
		e       *edge
		t       float64
		point   orb.Point
	}{{start, nearestEdge(first.Point, edges), 0, sc}, {end, nearestEdge(second.Point, edges), 0, ec}} {
		if connector.e == nil {
			continue
		}
		edge := connector.e
		t := projectRatio(connector.point, edge.Coordinates[0], edge.Coordinates[len(edge.Coordinates)-1])
		for _, item := range []struct {
			node   graphNode
			weight float64
		}{{edge.A, edge.Length * t}, {edge.B, edge.Length * (1 - t)}} {
			d := haversine(connector.point, coords[item.node])
			graph[connector.virtual] = append(graph[connector.virtual], arc{item.node, edge, item.weight + d})
			graph[item.node] = append(graph[item.node], arc{connector.virtual, edge, item.weight + d})
		}
	}
	modes := []struct {
		label              string
		branch, switchCost float64
		main               bool
	}{{"线路连续优先", .45, 18000, false}, {"物理距离较短", 0, 0, false}, {"正线优先", 1.25, 35000, true}}
	out := make([]map[string]any, 0, 3)
	seen := map[string]bool{}
	for mi, mode := range modes {
		path, pathEdges, ok := shortest(graph, coords, start, end, mode.branch, mode.switchCost, mode.main)
		if !ok {
			continue
		}
		signature := ""
		for _, e := range pathEdges {
			if e != nil {
				signature += fmt.Sprintf("%d,", e.ID)
			}
		}
		if seen[signature] {
			continue
		}
		seen[signature] = true
		points := make([][]float64, 0, len(path))
		distance := 0.0
		for i, n := range path {
			p := coords[n]
			xy := []float64{p[0], p[1]}
			if len(points) == 0 || points[len(points)-1][0] != xy[0] || points[len(points)-1][1] != xy[1] {
				points = append(points, xy)
			}
			if i > 0 {
				distance += haversine(coords[path[i-1]], p)
			}
		}
		lineLengths := map[string]float64{}
		for _, e := range pathEdges {
			if e == nil {
				continue
			}
			name := e.Name
			if name == "" {
				name = e.RelationName
				if name == "" {
					name = e.Ref
				}
			}
			if name != "" {
				lineLengths[name] += e.Length
			}
		}
		names := make([]string, 0, len(lineLengths))
		for name := range lineLengths {
			names = append(names, name)
		}
		sort.Slice(names, func(i, j int) bool { return lineLengths[names[i]] > lineLengths[names[j]] })
		out = append(out, map[string]any{"sourceLayer": fmt.Sprintf("osm-route-%d", mi), "label": mode.label, "coordinates": points, "distanceKm": math.Round(distance/100) / 10, "railwayLines": names})
	}
	return out, nil
}

func nearestEdge(point orb.Point, edges []*edge) *edge {
	var best *edge
	distance := math.Inf(1)
	for _, e := range edges {
		for i := 0; i < len(e.Coordinates)-1; i++ {
			_, _, d := project(point, e.Coordinates[i], e.Coordinates[i+1])
			if d < distance {
				distance, best = d, e
			}
		}
	}
	if distance > 5000 {
		return nil
	}
	return best
}
func snap(point orb.Point, edges []*edge, token int64) (graphNode, orb.Point, bool) {
	e := nearestEdge(point, edges)
	if e == nil {
		return graphNode{}, orb.Point{}, false
	}
	projected, _, _ := project(point, e.Coordinates[0], e.Coordinates[len(e.Coordinates)-1])
	return graphNode{OSM: token, Layer: e.A.Layer}, projected, true
}
func project(p, a, b orb.Point) (orb.Point, float64, float64) {
	sx := 111320 * math.Cos(p[1]*math.Pi/180)
	dx, dy := (b[0]-a[0])*sx, (b[1]-a[1])*111320
	px, py := (p[0]-a[0])*sx, (p[1]-a[1])*111320
	den := dx*dx + dy*dy
	t := 0.0
	if den > 0 {
		t = math.Max(0, math.Min(1, (px*dx+py*dy)/den))
	}
	q := orb.Point{a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t}
	return q, t, math.Hypot(px-dx*t, py-dy*t)
}
func projectRatio(p, a, b orb.Point) float64 { _, t, _ := project(p, a, b); return t }
func haversine(a, b orb.Point) float64 {
	lat1, lat2 := a[1]*math.Pi/180, b[1]*math.Pi/180
	dlat := (b[1] - a[1]) * math.Pi / 180
	dlon := (b[0] - a[0]) * math.Pi / 180
	x := math.Sin(dlat/2)*math.Sin(dlat/2) + math.Cos(lat1)*math.Cos(lat2)*math.Sin(dlon/2)*math.Sin(dlon/2)
	return 12742017.6 * math.Asin(math.Sqrt(x))
}
func shortest(graph map[graphNode][]arc, coords map[graphNode]orb.Point, start, end graphNode, branch, switchCost float64, main bool) ([]graphNode, []*edge, bool) {
	initial := pathState{Node: start}
	dist := map[pathState]float64{initial: 0}
	prev := map[pathState]struct {
		from pathState
		e    *edge
	}{}
	q := priorityQueue{&queueItem{State: initial}}
	heap.Init(&q)
	var target pathState
	found := false
	for q.Len() > 0 {
		item := heap.Pop(&q).(*queueItem)
		if item.Cost != dist[item.State] {
			continue
		}
		if item.State.Node == end {
			target = item.State
			found = true
			break
		}
		for _, a := range graph[item.State.Node] {
			cost := a.Cost
			if a.Edge != nil {
				if a.Edge.Usage == "branch" {
					cost *= 1 + branch
				}
				if main && a.Edge.Railway != "rail" {
					cost *= 1.25
				}
				if item.State.Group != "" && a.Edge.Groups != "" && item.State.Group != a.Edge.Groups {
					cost += switchCost
				}
			}
			group := item.State.Group
			if a.Edge != nil && a.Edge.Groups != "" {
				group = a.Edge.Groups
			}
			next := pathState{a.To, group}
			candidate := item.Cost + cost
			previousCost, exists := dist[next]
			if !exists || candidate < previousCost {
				dist[next] = candidate
				prev[next] = struct {
					from pathState
					e    *edge
				}{item.State, a.Edge}
				heuristic := haversine(coords[a.To], coords[end]) * .85
				heap.Push(&q, &queueItem{State: next, Cost: candidate, Priority: candidate + heuristic})
			}
		}
	}
	if !found {
		return nil, nil, false
	}
	nodes := []graphNode{target.Node}
	edges := []*edge{}
	for target != initial {
		p := prev[target]
		edges = append(edges, p.e)
		target = p.from
		nodes = append(nodes, target.Node)
	}
	for i, j := 0, len(nodes)-1; i < j; i, j = i+1, j-1 {
		nodes[i], nodes[j] = nodes[j], nodes[i]
	}
	for i, j := 0, len(edges)-1; i < j; i, j = i+1, j-1 {
		edges[i], edges[j] = edges[j], edges[i]
	}
	return nodes, edges, true
}
