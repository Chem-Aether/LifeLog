package main

import (
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"lifelog/goserver/internal/app"
)

func main() {
	gin.SetMode(gin.ReleaseMode)
	application, err := app.New()
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
