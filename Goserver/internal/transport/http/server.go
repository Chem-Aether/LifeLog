package http

import (
	"database/sql"
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
	"lifelog/goserver/internal/config"
	"lifelog/goserver/internal/modules/tiles"
	"lifelog/goserver/internal/platform/database"
	"lifelog/goserver/internal/transport/response"
)

type Server struct {
	config config.Config
	dbs    *database.Databases
	router *gin.Engine
	tiles  *tiles.Registry
}

func NewServer(cfg config.Config, dbs *database.Databases, tileRegistry *tiles.Registry) *Server {
	server := &Server{config: cfg, dbs: dbs, tiles: tileRegistry, router: gin.New()}
	server.router.Use(gin.Logger(), gin.Recovery(), CORS(cfg.Server.CORS))
	server.routes()
	return server
}

func (s *Server) Address() string {
	return fmt.Sprintf("%s:%d", s.config.Server.Host, s.config.Server.Port)
}
func (s *Server) Handler() http.Handler  { return s.router }
func (s *Server) db(name string) *sql.DB { return s.dbs.Get(name) }

func payload(c *gin.Context, data any) { response.Payload(c, data) }
