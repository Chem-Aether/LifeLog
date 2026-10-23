package catalog

import (
	"database/sql"
	"math"
	"sort"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"lifelog/goserver/internal/transport/response"
)

type Handler struct{ poi *sql.DB }

func NewHandler(poi *sql.DB) *Handler { return &Handler{poi: poi} }
func (h *Handler) Register(r gin.IRoutes) {
	r.GET("/api/pois/search", h.searchPOIs)
	r.GET("/api/pois/nearby", h.nearbyPOIs)
	r.GET("/api/pois/stats", h.poiStats)
	r.GET("/api/pois/:identifier", h.poiByID)
}

func limitParam(c *gin.Context, fallback int) int {
	limit, err := strconv.Atoi(c.Query("limit"))
	if err != nil || limit < 1 {
		return fallback
	}
	if limit > 100 {
		return 100
	}
	return limit
}

// searchPOIs godoc
// @Summary Search points of interest
// @Tags catalog
// @Produce json
// @Param q query string false "Search text"
// @Param keyword query string false "Alternative search text"
// @Param category query string false "POI category"
// @Param limit query integer false "Maximum result count"
// @Success 200 {object} map[string]interface{}
// @Router /api/pois/search [get]
func (a *Handler) searchPOIs(c *gin.Context) {
	keyword := strings.TrimSpace(c.DefaultQuery("q", c.Query("keyword")))
	category := strings.TrimSpace(c.Query("category"))
	if keyword == "" {
		response.Payload(c, []any{})
		return
	}
	query := `SELECT poi_id,name,name_zh,aliases,category,subcategory,longitude,latitude,address,website,opening_hours,wikidata,importance FROM poi WHERE (name LIKE ? OR name_zh LIKE ? OR aliases LIKE ? OR address LIKE ?) AND (?='' OR category=?) ORDER BY CASE WHEN name=? OR name_zh=? THEN 0 WHEN name LIKE ? OR name_zh LIKE ? THEN 1 ELSE 2 END,importance DESC,name LIMIT ?`
	pattern := "%" + keyword + "%"
	rows, err := a.poi.Query(query, pattern, pattern, pattern, pattern, category, category, keyword, keyword, keyword+"%", keyword+"%", limitParam(c, 20))
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	items := make([]map[string]any, 0)
	for rows.Next() {
		var id, importance int
		var name, category, subcategory string
		var nameZh, aliases, address, website, hours, wikidata sql.NullString
		var lon, lat float64
		if err := rows.Scan(&id, &name, &nameZh, &aliases, &category, &subcategory, &lon, &lat, &address, &website, &hours, &wikidata, &importance); err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		items = append(items, poiRecord(id, name, nameZh, aliases, category, subcategory, lon, lat, address, website, hours, wikidata, importance))
	}
	response.Payload(c, items)
}

func poiRecord(id int, name string, nameZh, aliases sql.NullString, category, subcategory string, lon, lat float64, address, website, hours, wikidata sql.NullString, importance int) gin.H {
	return gin.H{"id": id, "name": name, "nameZh": nullable(nameZh), "aliases": nullable(aliases), "category": category, "subcategory": subcategory, "longitude": lon, "latitude": lat, "address": nullable(address), "website": nullable(website), "openingHours": nullable(hours), "wikidata": nullable(wikidata), "importance": importance}
}

func nullable(value sql.NullString) any {
	if value.Valid {
		return value.String
	}
	return nil
}

// poiByID godoc
// @Summary Get a point of interest
// @Tags catalog
// @Produce json
// @Param identifier path integer true "POI identifier"
// @Success 200 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Router /api/pois/{identifier} [get]
func (a *Handler) poiByID(c *gin.Context) {
	id, parseErr := strconv.ParseInt(c.Param("identifier"), 10, 64)
	if parseErr != nil {
		c.JSON(400, gin.H{"detail": "POI ID 无效"})
		return
	}
	var name, category, subcategory string
	var nameZh, aliases, address, website, hours, wikidata sql.NullString
	var importance int
	var lon, lat float64
	err := a.poi.QueryRow(`SELECT name,name_zh,aliases,category,subcategory,longitude,latitude,address,website,opening_hours,wikidata,importance FROM poi WHERE poi_id=?`, id).Scan(&name, &nameZh, &aliases, &category, &subcategory, &lon, &lat, &address, &website, &hours, &wikidata, &importance)
	if err == sql.ErrNoRows {
		c.JSON(404, gin.H{"detail": "POI不存在"})
		return
	}
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	response.Payload(c, poiRecord(int(id), name, nameZh, aliases, category, subcategory, lon, lat, address, website, hours, wikidata, importance))
}

// nearbyPOIs godoc
// @Summary Search points of interest near a coordinate
// @Tags catalog
// @Produce json
// @Param longitude query number false "Longitude"
// @Param latitude query number false "Latitude"
// @Param radius query number false "Search radius in meters"
// @Param category query string false "POI category"
// @Param limit query integer false "Maximum result count"
// @Success 200 {object} map[string]interface{}
// @Router /api/pois/nearby [get]
func (a *Handler) nearbyPOIs(c *gin.Context) {
	lon, e1 := strconv.ParseFloat(c.DefaultQuery("longitude", c.Query("lng")), 64)
	lat, e2 := strconv.ParseFloat(c.DefaultQuery("latitude", c.Query("lat")), 64)
	radius, e3 := strconv.ParseFloat(c.DefaultQuery("radius", "5000"), 64)
	if e1 != nil || e2 != nil || e3 != nil || lon < -180 || lon > 180 || lat < -90 || lat > 90 || radius < 50 || radius > 100000 {
		response.BadRequest(c, "经纬度或半径参数无效")
		return
	}
	category := c.Query("category")
	latDelta := radius / 110574.0
	lonDelta := radius / (111320.0 * math.Max(math.Cos(lat*math.Pi/180), 0.01))
	query := `SELECT p.poi_id,p.name,p.name_zh,p.aliases,p.category,p.subcategory,p.longitude,p.latitude,p.address,p.website,p.opening_hours,p.wikidata,p.importance FROM rtree_poi_geom r JOIN poi p ON p.poi_id=r.id WHERE r.minx<=? AND r.maxx>=? AND r.miny<=? AND r.maxy>=? AND (?='' OR p.category=?) ORDER BY p.importance DESC LIMIT 1000`
	rows, err := a.poi.Query(query, lon+lonDelta, lon-lonDelta, lat+latDelta, lat-latDelta, category, category)
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id int
		var name, cat, subcat string
		var nameZh, aliases, address, website, hours, wikidata sql.NullString
		var importance int
		var x, y float64
		if err := rows.Scan(&id, &name, &nameZh, &aliases, &cat, &subcat, &x, &y, &address, &website, &hours, &wikidata, &importance); err != nil {
			continue
		}
		distance := math.Round(haversine(lon, lat, x, y))
		if distance <= radius {
			item := poiRecord(id, name, nameZh, aliases, cat, subcat, x, y, address, website, hours, wikidata, importance)
			item["distance"] = int(distance)
			items = append(items, item)
		}
	}
	sort.Slice(items, func(i, j int) bool {
		left, right := items[i]["distance"].(int), items[j]["distance"].(int)
		if left == right {
			return items[i]["importance"].(int) > items[j]["importance"].(int)
		}
		return left < right
	})
	if len(items) > limitParam(c, 20) {
		items = items[:limitParam(c, 20)]
	}
	response.Payload(c, items)
}

func haversine(lon1, lat1, lon2, lat2 float64) float64 {
	const earth = 6371000
	rad := math.Pi / 180
	dlat := (lat2 - lat1) * rad
	dlon := (lon2 - lon1) * rad
	a := math.Sin(dlat/2)*math.Sin(dlat/2) + math.Cos(lat1*rad)*math.Cos(lat2*rad)*math.Sin(dlon/2)*math.Sin(dlon/2)
	return 2 * earth * math.Asin(math.Sqrt(a))
}

// poiStats godoc
// @Summary Get POI category counts
// @Tags catalog
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /api/pois/stats [get]
func (a *Handler) poiStats(c *gin.Context) {
	rows, err := a.poi.Query(`SELECT category,COUNT(*) FROM poi GROUP BY category ORDER BY COUNT(*) DESC`)
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	categories := make([]gin.H, 0)
	var total int64
	for rows.Next() {
		var name string
		var count int64
		if err := rows.Scan(&name, &count); err == nil {
			categories = append(categories, gin.H{"category": name, "count": count})
			total += count
		}
	}
	metadata := map[string]string{}
	metaRows, err := a.poi.Query(`SELECT key,value FROM geo_metadata WHERE key LIKE 'poi.%'`)
	if err == nil {
		defer metaRows.Close()
		for metaRows.Next() {
			var k, v string
			if metaRows.Scan(&k, &v) == nil {
				metadata[k] = v
			}
		}
	}
	response.Payload(c, gin.H{"total": total, "categories": categories, "metadata": metadata})
}
