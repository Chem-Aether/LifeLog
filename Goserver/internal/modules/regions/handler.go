package regions

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/paulmach/orb"
	"lifelog/goserver/internal/platform/geospatial"
	"lifelog/goserver/internal/transport/response"
)

type Handler struct{ db *sql.DB }
type node struct {
	ID          string  `json:"id"`
	ParentID    *string `json:"parentId"`
	CountryCode string  `json:"countryCode"`
	RegionCode  *string `json:"regionCode"`
	Level       int     `json:"level"`
	Name        string  `json:"name"`
	Type        *string `json:"type"`
}
type pointInput struct {
	ID        any      `json:"id"`
	Longitude *float64 `json:"longitude"`
	Latitude  *float64 `json:"latitude"`
	Lng       *float64 `json:"lng"`
	Lat       *float64 `json:"lat"`
}

func NewHandler(db *sql.DB) *Handler { return &Handler{db: db} }
func (h *Handler) Register(router gin.IRoutes) {
	router.GET("/api/regions/search", h.search)
	router.GET("/api/regions/reverse", h.reverse)
	router.POST("/api/regions/reverse/batch", h.reverseBatch)
	router.POST("/api/regions/paths", h.paths)
	router.POST("/api/regions/boundaries", h.boundaries)
	router.GET("/api/regions/:node_id", h.byID)
}

const nodeColumns = `node_id,parent_id,country_code,region_code,level,name,type`

func scanNode(row interface{ Scan(...any) error }) (node, error) {
	var n node
	err := row.Scan(&n.ID, &n.ParentID, &n.CountryCode, &n.RegionCode, &n.Level, &n.Name, &n.Type)
	return n, err
}
func (h *Handler) path(id string) ([]node, error) {
	chain := make([]node, 0, 5)
	seen := map[string]bool{}
	for id != "" && !seen[id] {
		seen[id] = true
		n, err := scanNode(h.db.QueryRow(`SELECT `+nodeColumns+` FROM admin_boundaries WHERE node_id=?`, id))
		if err == sql.ErrNoRows {
			break
		}
		if err != nil {
			return nil, err
		}
		chain = append(chain, n)
		if n.ParentID == nil {
			break
		}
		id = *n.ParentID
	}
	for i, j := 0, len(chain)-1; i < j; i, j = i+1, j-1 {
		chain[i], chain[j] = chain[j], chain[i]
	}
	return chain, nil
}
func (h *Handler) record(id string) (map[string]any, bool, error) {
	var version string
	err := h.db.QueryRow(`SELECT data_version FROM admin_boundaries WHERE node_id=?`, id).Scan(&version)
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	path, err := h.path(id)
	return map[string]any{"path": path, "dataVersion": version}, true, err
}

// search godoc
// @Summary Search administrative regions
// @Tags regions
// @Produce json
// @Param keyword query string true "Search text"
// @Param level query integer false "Administrative level"
// @Param country_code query string false "Country code"
// @Success 200 {object} map[string]interface{}
// @Router /api/regions/search [get]
func (h *Handler) search(c *gin.Context) {
	keyword := strings.TrimSpace(c.Query("keyword"))
	if keyword == "" {
		response.Payload(c, []any{})
		return
	}
	keywordRunes := []rune(keyword)
	if len(keywordRunes) > 80 {
		keyword = string(keywordRunes[:80])
	}
	level, _ := strconv.Atoi(c.DefaultQuery("level", "0"))
	country := strings.TrimSpace(c.Query("country_code"))
	like := "%" + keyword + "%"
	rows, err := h.db.Query(`SELECT node_id FROM admin_boundaries WHERE (name LIKE ? OR region_code LIKE ?) AND (?=0 OR level=?) AND (?='' OR country_code=?) ORDER BY CASE WHEN name=? OR region_code=? THEN 0 ELSE 1 END,level,name LIMIT 20`, like, like, level, level, country, country, keyword, keyword)
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	items := make([]map[string]any, 0)
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			continue
		}
		record, ok, err := h.record(id)
		if err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		if ok {
			items = append(items, record)
		}
	}
	response.Payload(c, items)
}

// byID godoc
// @Summary Get an administrative region and its ancestor path
// @Tags regions
// @Produce json
// @Param node_id path string true "Region node ID"
// @Success 200 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Router /api/regions/{node_id} [get]
func (h *Handler) byID(c *gin.Context) {
	record, ok, err := h.record(c.Param("node_id"))
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	if !ok {
		c.JSON(404, gin.H{"detail": "行政区不存在"})
		return
	}
	response.Payload(c, record)
}

// paths godoc
// @Summary Get administrative paths for region IDs
// @Tags regions
// @Accept json
// @Produce json
// @Param request body map[string]interface{} true "Region IDs"
// @Success 200 {object} map[string]interface{}
// @Router /api/regions/paths [post]
func (h *Handler) paths(c *gin.Context) {
	var body struct {
		IDs []string `json:"ids"`
	}
	if c.ShouldBindJSON(&body) != nil {
		response.BadRequest(c, "请求参数无效")
		return
	}
	if len(body.IDs) > 500 {
		response.BadRequest(c, "行政区节点 ID 列表最多 500 项")
		return
	}
	result := make(map[string]any)
	for _, id := range body.IDs {
		if _, exists := result[id]; exists {
			continue
		}
		record, ok, err := h.record(id)
		if err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		if ok {
			result[id] = record
		}
	}
	response.Payload(c, result)
}

// boundaries godoc
// @Summary Get GeoJSON boundaries for region IDs
// @Tags regions
// @Accept json
// @Produce application/geo+json
// @Param request body map[string]interface{} true "Region IDs"
// @Success 200 {object} map[string]interface{}
// @Router /api/regions/boundaries [post]
func (h *Handler) boundaries(c *gin.Context) {
	var body struct {
		IDs []string `json:"ids"`
	}
	if c.ShouldBindJSON(&body) != nil {
		response.BadRequest(c, "请求参数无效")
		return
	}
	if len(body.IDs) > 500 {
		response.BadRequest(c, "行政区 ID 列表最多 500 项")
		return
	}
	features := make([]map[string]any, 0, len(body.IDs))
	seen := map[string]bool{}
	for _, id := range body.IDs {
		if seen[id] {
			continue
		}
		seen[id] = true
		var n node
		var raw []byte
		var minLon, minLat, maxLon, maxLat float64
		var version string
		err := h.db.QueryRow(`SELECT node_id,parent_id,country_code,region_code,level,name,type,min_lon,min_lat,max_lon,max_lat,data_version,geom FROM admin_boundaries WHERE node_id=?`, id).Scan(&n.ID, &n.ParentID, &n.CountryCode, &n.RegionCode, &n.Level, &n.Name, &n.Type, &minLon, &minLat, &maxLon, &maxLat, &version, &raw)
		if err == sql.ErrNoRows {
			continue
		}
		if err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		if _, err := geospatial.DecodeGeoPackage(raw); err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		features = append(features, map[string]any{"type": "Feature", "id": id, "bbox": []float64{minLon, minLat, maxLon, maxLat}, "properties": gin.H{"id": n.ID, "parentId": n.ParentID, "countryCode": n.CountryCode, "regionCode": n.RegionCode, "level": n.Level, "name": n.Name, "type": n.Type, "dataVersion": version}, "geometry": geospatial.GeometryJSON(raw)})
	}
	bodyJSON, _ := json.Marshal(gin.H{"type": "FeatureCollection", "features": features})
	c.Data(http.StatusOK, "application/geo+json", bodyJSON)
}

// reverse godoc
// @Summary Find the administrative path containing a coordinate
// @Tags regions
// @Produce json
// @Param longitude query number true "Longitude"
// @Param latitude query number true "Latitude"
// @Success 200 {object} map[string]interface{}
// @Router /api/regions/reverse [get]
func (h *Handler) reverse(c *gin.Context) {
	lon, e1 := strconv.ParseFloat(c.Query("longitude"), 64)
	lat, e2 := strconv.ParseFloat(c.Query("latitude"), 64)
	if e1 != nil || e2 != nil || !valid(lon, lat) {
		response.BadRequest(c, "经纬度参数无效")
		return
	}
	result, err := h.reversePoint(lon, lat)
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	response.Payload(c, result)
}

// reverseBatch godoc
// @Summary Reverse lookup administrative paths for coordinates
// @Tags regions
// @Accept json
// @Produce json
// @Param request body map[string]interface{} true "Coordinate list"
// @Success 200 {object} map[string]interface{}
// @Router /api/regions/reverse/batch [post]
func (h *Handler) reverseBatch(c *gin.Context) {
	var body struct {
		Points []pointInput `json:"points"`
	}
	if c.ShouldBindJSON(&body) != nil {
		response.BadRequest(c, "请求参数无效")
		return
	}
	if len(body.Points) > 500 {
		response.BadRequest(c, "一次最多查询500个坐标")
		return
	}
	results := make([]gin.H, 0, len(body.Points))
	for _, p := range body.Points {
		lon, lat := p.Longitude, p.Latitude
		if lon == nil {
			lon = p.Lng
		}
		if lat == nil {
			lat = p.Lat
		}
		if lon == nil || lat == nil || !valid(*lon, *lat) {
			results = append(results, gin.H{"id": p.ID, "error": "经纬度参数无效"})
			continue
		}
		record, err := h.reversePoint(*lon, *lat)
		if err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		item := record.(gin.H)
		item["id"] = p.ID
		results = append(results, item)
	}
	response.Payload(c, results)
}
func valid(lon, lat float64) bool { return lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90 }
func (h *Handler) reversePoint(lon, lat float64) (any, error) {
	rows, err := h.db.Query(`SELECT b.node_id,b.data_version,b.level,b.bbox_area,b.geom FROM rtree_admin_boundaries_geom r JOIN admin_boundaries b ON b.fid=r.id WHERE r.minx<=? AND r.maxx>=? AND r.miny<=? AND r.maxy>=? ORDER BY b.level DESC,b.bbox_area ASC`, lon, lon, lat, lat)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	point := orb.Point{lon, lat}
	var matchID, version string
	var found bool
	for rows.Next() {
		var id, ver string
		var level int
		var area float64
		var raw []byte
		if err := rows.Scan(&id, &ver, &level, &area, &raw); err != nil {
			return nil, err
		}
		g, err := geospatial.DecodeGeoPackage(raw)
		if err != nil {
			return nil, err
		}
		if geospatial.GeometryContains(g, point) {
			matchID, version, found = id, ver, true
			break
		}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	path := []node{}
	if found {
		path, err = h.path(matchID)
		if err != nil {
			return nil, err
		}
	}
	return gin.H{"longitude": lon, "latitude": lat, "path": path, "dataVersion": func() any {
		if found {
			return version
		}
		return nil
	}()}, nil
}
