package response

import (
	"github.com/gin-gonic/gin"
	"net/http"
)

func Payload(c *gin.Context, data any) {
	c.JSON(http.StatusOK, gin.H{"msg": "操作成功", "data": data})
}
func BadRequest(c *gin.Context, message string) {
	c.JSON(http.StatusBadRequest, gin.H{"detail": message})
}
