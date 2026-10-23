// @title LifeLog Go Map Service API
// @version 1.0
// @description API documentation for the LifeLog map and geospatial service.
// @BasePath /
package main

import (
	"flag"
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"lifelog/goserver/internal/app"
)

func main() {
	configPath := flag.String("config", "config/application.json", "path to the application configuration file")
	flag.Parse()
	gin.SetMode(gin.ReleaseMode)
	application, err := app.New(*configPath)
	if err != nil {
		log.Fatal(err)
	}
	address := application.Address()
	server := &http.Server{
		Addr:              address,
		Handler:           application.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
	}
	log.Printf("Goserver listening on %s", address)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
