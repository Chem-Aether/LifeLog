package railway

import (
	"database/sql"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/paulmach/orb"
	"lifelog/goserver/internal/platform/geospatial"
	"lifelog/goserver/internal/transport/response"
)

type Handler struct {
	network, routing *sql.DB
	spiderURL        string
	client           *http.Client
}

func NewHandler(network, routing *sql.DB, spiderURL string) *Handler {
	return &Handler{network: network, routing: routing, spiderURL: strings.TrimRight(spiderURL, "/"), client: &http.Client{Timeout: 12 * time.Second}}
}
func (h *Handler) Register(r gin.IRoutes) {
	r.GET("/api/railway/stations/search", h.searchStations)
	r.POST("/api/railway/routes/sample", h.sampleRoute)
}

// searchStations godoc
// @Summary Search railway stations
// @Tags railway
// @Produce json
// @Param keyword query string true "Station name, code, city, or pinyin"
// @Param limit query integer false "Maximum result count"
// @Success 200 {object} map[string]interface{}
// @Router /api/railway/stations/search [get]
func (h *Handler) searchStations(c *gin.Context) {
	q := strings.TrimSpace(c.Query("keyword"))
	if q == "" {
		response.Payload(c, []any{})
		return
	}
	if len([]rune(q)) > 80 {
		q = string([]rune(q)[:80])
	}
	upper := strings.ToUpper(q)
	like := "%" + q + "%"
	rows, err := h.network.Query(`SELECT name,code,city,pinyin,geom FROM railway_stations WHERE name LIKE ? OR code LIKE ? OR city LIKE ? OR pinyin LIKE ? ORDER BY CASE WHEN name=? OR code=? THEN 0 WHEN name LIKE ? OR code LIKE ? THEN 1 ELSE 2 END,name LIMIT ?`, like, "%"+upper+"%", like, "%"+strings.ToLower(q)+"%", q, upper, q+"%", upper+"%", limit(c.Query("limit"), 20, 50))
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var name string
		var code, city, pinyin sql.NullString
		var blob []byte
		if rows.Scan(&name, &code, &city, &pinyin, &blob) != nil {
			continue
		}
		coords, err := pointCoordinates(blob)
		if err != nil {
			continue
		}
		items = append(items, gin.H{"name": name, "code": value(code), "city": value(city), "longitude": coords[0], "latitude": coords[1]})
	}
	response.Payload(c, items)
}
func limit(raw string, fallback, max int) int {
	var n int
	for _, r := range raw {
		if r < '0' || r > '9' {
			return fallback
		}
		n = n*10 + int(r-'0')
		if n > max {
			return max
		}
	}
	if n < 1 {
		return fallback
	}
	if n > max {
		return max
	}
	return n
}
func value(v sql.NullString) string {
	if v.Valid {
		return v.String
	}
	return ""
}
func pointCoordinates(blob []byte) ([2]float64, error) {
	g, err := geospatial.DecodeGeoPackage(blob)
	if err != nil {
		return [2]float64{}, err
	}
	p, ok := g.(orb.Point)
	if !ok {
		return [2]float64{}, http.ErrNotSupported
	}
	return [2]float64{p[0], p[1]}, nil
}

// sampleRoute godoc
// @Summary Generate route candidates between ordered railway stations
// @Tags railway
// @Accept json
// @Produce json
// @Param request body map[string]interface{} true "Ordered station names"
// @Success 200 {object} map[string]interface{}
// @Router /api/railway/routes/sample [post]
func (h *Handler) sampleRoute(c *gin.Context) {
	var req struct {
		Stations  []string `json:"stations"`
		TrainCode string   `json:"train_code"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "请求参数无效")
		return
	}
	for i := range req.Stations {
		req.Stations[i] = strings.TrimSpace(req.Stations[i])
	}
	if len(req.Stations) < 2 || len(req.Stations) > 200 {
		response.BadRequest(c, "途经站数量必须为 2 至 200 个")
		return
	}
	result, err := h.Sample(req.Stations, req.TrainCode)
	if err != nil {
		c.JSON(400, gin.H{"detail": err.Error()})
		return
	}
	response.Payload(c, result)
}
