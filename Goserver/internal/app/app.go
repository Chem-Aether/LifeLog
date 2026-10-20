package app

import (
	"fmt"
	"os"
	"path/filepath"

	"lifelog/goserver/internal/config"
	"lifelog/goserver/internal/modules/tiles"
	"lifelog/goserver/internal/platform/database"
	httpapi "lifelog/goserver/internal/transport/http"
)

func New() (*httpapi.Server, error) {
	root, err := findRoot()
	if err != nil {
		return nil, err
	}
	cfg, err := config.Load(filepath.Join(root, "config", "application.json"))
	if err != nil {
		return nil, err
	}
	dbs, err := database.Open(root, cfg.Storage)
	if err != nil {
		return nil, err
	}
	tileRegistry, err := tiles.NewRegistry(filepath.Join(root, cfg.Storage.DataDir), cfg.Tiles.Sources, cfg.Tiles.CacheSize)
	if err != nil {
		_ = dbs.Close()
		return nil, err
	}
	return httpapi.NewServer(cfg, dbs, tileRegistry), nil
}

func findRoot() (string, error) {
	current, _ := os.Getwd()
	executable, _ := os.Executable()
	for _, start := range []string{current, filepath.Dir(executable)} {
		if start == "" {
			continue
		}
		for dir := start; ; dir = filepath.Dir(dir) {
			if _, err := os.Stat(filepath.Join(dir, "config", "application.json")); err == nil {
				return dir, nil
			}
			parent := filepath.Dir(dir)
			if parent == dir {
				break
			}
		}
	}
	return "", fmt.Errorf("Goserver config/application.json was not found beside the executable or in the working directory ancestry")
}
