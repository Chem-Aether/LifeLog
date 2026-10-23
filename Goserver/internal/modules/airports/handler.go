package airports

import (
	"database/sql"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/paulmach/orb"
	"lifelog/goserver/internal/platform/geospatial"
	"lifelog/goserver/internal/transport/response"
)

type Handler struct{ db *sql.DB }

func NewHandler(db *sql.DB) *Handler { return &Handler{db: db} }

func (h *Handler) Register(r gin.IRoutes) {
	r.GET("/api/airports/search", h.search)
	r.POST("/api/airports/by-codes", h.byCodes)
}

// search godoc
// @Summary Search airports
// @Tags airports
// @Produce json
// @Param keyword query string false "Airport, city, ICAO, or IATA"
// @Param limit query integer false "Maximum result count"
// @Success 200 {object} map[string]interface{}
// @Router /api/airports/search [get]
func (h *Handler) search(c *gin.Context) {
	keyword := strings.TrimSpace(c.Query("keyword"))
	pattern := "%" + keyword + "%"
	upper := strings.ToUpper(keyword)
	rows, err := h.db.Query(`SELECT icao,iata,name,attr,city,geom FROM airports WHERE (?='' OR name LIKE ? OR city LIKE ? OR icao LIKE ? OR iata LIKE ?) ORDER BY CASE WHEN name=? OR icao=? OR iata=? THEN 0 ELSE 1 END,name LIMIT ?`, keyword, pattern, pattern, "%"+upper+"%", "%"+upper+"%", keyword, upper, upper, queryLimit(c.Query("limit")))
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var icao, name string
		var iata, attr, city sql.NullString
		var geom []byte
		if err := rows.Scan(&icao, &iata, &name, &attr, &city, &geom); err != nil {
			continue
		}
		point, ok := decodePoint(geom)
		if !ok {
			continue
		}
		items = append(items, airportRecord(icao, iata, name, city, attr, point))
	}
	response.Payload(c, items)
}

// byCodes godoc
// @Summary Get airports by ICAO or IATA codes
// @Tags airports
// @Accept json
// @Produce json
// @Param request body map[string]interface{} true "Airport codes"
// @Success 200 {object} map[string]interface{}
// @Router /api/airports/by-codes [post]
func (h *Handler) byCodes(c *gin.Context) {
	var request struct {
		Codes []string `json:"codes"`
	}
	if err := c.ShouldBindJSON(&request); err != nil {
		response.BadRequest(c, "请求参数无效")
		return
	}
	result := make([]gin.H, 0, len(request.Codes))
	seen := map[string]bool{}
	for _, code := range request.Codes {
		code = strings.ToUpper(strings.TrimSpace(code))
		if code == "" || seen[code] {
			continue
		}
		seen[code] = true
		if len(seen) > 1000 {
			response.BadRequest(c, "机场代码最多1000项")
			return
		}
		var icao, name string
		var iata, attr, city sql.NullString
		var geom []byte
		err := h.db.QueryRow(`SELECT icao,iata,name,city,attr,geom FROM airports WHERE iata=? OR icao=?`, code, code).Scan(&icao, &iata, &name, &city, &attr, &geom)
		if err == sql.ErrNoRows {
			continue
		}
		if err != nil {
			c.JSON(500, gin.H{"detail": err.Error()})
			return
		}
		point, ok := decodePoint(geom)
		if !ok {
			continue
		}
		result = append(result, airportRecord(icao, iata, name, city, attr, point))
	}
	response.Payload(c, result)
}

func queryLimit(raw string) int {
	limit, err := strconv.Atoi(raw)
	if err != nil || limit < 1 {
		return 20
	}
	if limit > 100 {
		return 100
	}
	return limit
}

func decodePoint(raw []byte) (orb.Point, bool) {
	geometry, err := geospatial.DecodeGeoPackage(raw)
	if err != nil {
		return orb.Point{}, false
	}
	point, ok := geometry.(orb.Point)
	return point, ok
}

func airportRecord(icao string, iata sql.NullString, name string, city, attr sql.NullString, point orb.Point) gin.H {
	return gin.H{"icao": icao, "iata": nullable(iata), "name": name, "city": nullable(city), "attr": nullable(attr), "longitude": point[0], "latitude": point[1]}
}

func nullable(value sql.NullString) any {
	if value.Valid {
		return value.String
	}
	return nil
}
