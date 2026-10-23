package http

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	_ "lifelog/goserver/docs"
	"lifelog/goserver/internal/modules/airports"
	"lifelog/goserver/internal/modules/catalog"
	"lifelog/goserver/internal/modules/railway"
	"lifelog/goserver/internal/modules/regions"
)

func (a *Server) routes() {
	if a.config.APIDocs.Enabled {
		a.router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
	}
	a.router.GET("/health", health)
	regions.NewHandler(a.db("regions")).Register(a.router)
	railway.NewHandler(a.db("network"), a.db("routing"), a.config.Integrations.SpiderServer.BaseURL).Register(a.router)
	catalog.NewHandler(a.db("poi")).Register(a.router)
	airports.NewHandler(a.db("airports")).Register(a.router)
	a.router.GET("/api/tiles/tilejson.json", a.tileJSON)
	a.router.GET("/api/tiles/:z/:x/:y.mvt", a.vectorTile)
	a.router.GET("/api/maps/catalog", a.mapCatalog)
	a.router.GET("/api/maps/status", a.mapStatus)
	a.router.POST("/api/admin/maps/reload", a.reloadMaps)
	a.router.NoRoute(func(c *gin.Context) { c.JSON(http.StatusNotFound, gin.H{"detail": "Not Found"}) })
}

// health godoc
// @Summary Check service health
// @Tags system
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /health [get]
func health(c *gin.Context) { payload(c, gin.H{"status": "ok"}) }

// tileJSON godoc
// @Summary Get the basemap TileJSON document
// @Tags maps
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /api/tiles/tilejson.json [get]
func (a *Server) tileJSON(c *gin.Context) {
	base := fmt.Sprintf("%s://%s", c.Request.URL.Scheme, c.Request.Host)
	if c.Request.TLS != nil {
		base = "https://" + c.Request.Host
	}
	if c.Request.URL.Scheme == "" && c.Request.TLS == nil {
		base = "http://" + c.Request.Host
	}
	c.JSON(http.StatusOK, gin.H{"tilejson": "3.0.0", "name": "EastBill unified basemap", "scheme": "xyz", "tiles": []string{base + "/api/tiles/{z}/{x}/{y}.mvt"}, "minzoom": a.config.Tiles.MinZoom, "maxzoom": a.config.Tiles.MaxZoom, "bounds": []float64{-180, -85.051129, 180, 85.051129}, "attribution": "© OpenStreetMap contributors"})
}

// vectorTile godoc
// @Summary Get a vector map tile
// @Tags maps
// @Produce application/vnd.mapbox-vector-tile
// @Param z path integer true "Zoom"
// @Param x path integer true "Tile X"
// @Param y path integer true "Tile Y"
// @Success 200 {string} string "Gzipped MVT data"
// @Router /api/tiles/{z}/{x}/{y}.mvt [get]
func (a *Server) vectorTile(c *gin.Context) {
	z, e1 := strconv.Atoi(c.Param("z"))
	x, e2 := strconv.Atoi(c.Param("x"))
	yParam := c.Param("y")
	if yParam == "" {
		yParam = c.Param("y.mvt")
	}
	y, e3 := strconv.Atoi(strings.TrimSuffix(yParam, ".mvt"))
	if e1 != nil || e2 != nil || e3 != nil || z < 0 || z > 22 || x < 0 || y < 0 || uint64(x) >= uint64(1)<<z || uint64(y) >= uint64(1)<<z {
		c.JSON(400, gin.H{"detail": "Invalid tile coordinate"})
		return
	}
	data, source, overzoomed, err := a.tiles.Tile(z, x, y)
	if err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	if len(data) == 0 {
		c.Status(http.StatusNoContent)
		return
	}
	c.Header("Content-Encoding", "gzip")
	c.Header("Cache-Control", "public, max-age=86400")
	c.Header("X-Map-Source", source)
	c.Header("X-Map-Overzoom", strconv.FormatBool(overzoomed))
	c.Data(http.StatusOK, "application/vnd.mapbox-vector-tile", data)
}

// mapCatalog godoc
// @Summary List available map sources
// @Tags maps
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /api/maps/catalog [get]
func (a *Server) mapCatalog(c *gin.Context) { payload(c, a.tiles.Sources()) }

// mapStatus godoc
// @Summary Get map source and tile cache status
// @Tags maps
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /api/maps/status [get]
func (a *Server) mapStatus(c *gin.Context) {
	payload(c, gin.H{"sources": a.tiles.Sources(), "cache": a.tiles.CacheInfo()})
}

// reloadMaps godoc
// @Summary Reload local map sources
// @Tags maps
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /api/admin/maps/reload [post]
func (a *Server) reloadMaps(c *gin.Context) {
	if err := a.tiles.Reload(); err != nil {
		c.JSON(500, gin.H{"detail": err.Error()})
		return
	}
	payload(c, gin.H{"sources": a.tiles.Sources()})
}
