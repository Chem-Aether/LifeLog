package http

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"lifelog/goserver/internal/config"
)

func CORS(settings config.CORS) gin.HandlerFunc {
	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		allowed := len(settings.Origins) == 0 || contains(settings.Origins, "*") || contains(settings.Origins, origin)
		if allowed && origin != "" {
			if contains(settings.Origins, "*") {
				c.Header("Access-Control-Allow-Origin", "*")
			} else {
				c.Header("Access-Control-Allow-Origin", origin)
			}
		}
		c.Header("Access-Control-Allow-Methods", strings.Join(settings.Methods, ", "))
		c.Header("Access-Control-Allow-Headers", strings.Join(settings.Headers, ", "))
		if c.Request.Method == http.MethodOptions {
			c.Status(http.StatusNoContent)
			c.Abort()
			return
		}
		c.Next()
	}
}

func contains(values []string, value string) bool {
	for _, item := range values {
		if item == value {
			return true
		}
	}
	return false
}
