package config

import (
	"encoding/json"
	"fmt"
	"os"
)

type Config struct {
	APIDocs struct {
		Enabled bool `json:"enabled"`
	} `json:"api_docs"`
	Server struct {
		Host string `json:"host"`
		Port int    `json:"port"`
		CORS CORS   `json:"cors"`
	} `json:"server"`
	Storage      Storage `json:"storage"`
	Integrations struct {
		SpiderServer struct {
			BaseURL string `json:"base_url"`
		} `json:"spyderserver"`
	} `json:"integrations"`
	Tiles Tiles `json:"tiles"`
}

type CORS struct {
	Origins []string `json:"origins"`
	Methods []string `json:"methods"`
	Headers []string `json:"headers"`
}
type Storage struct {
	DataDir         string `json:"data_dir"`
	RailwayNetwork  string `json:"railway_network"`
	RailwayRouting  string `json:"railway_routing"`
	AdminBoundaries string `json:"admin_boundaries"`
	Airports        string `json:"airports"`
	POI             string `json:"poi"`
}
type Tiles struct {
	CacheSize int          `json:"cache_size"`
	MinZoom   int          `json:"min_zoom"`
	MaxZoom   int          `json:"max_zoom"`
	Sources   []TileSource `json:"sources"`
}
type TileSource struct {
	ID           string     `json:"id"`
	File         string     `json:"file"`
	Priority     int        `json:"priority"`
	ServeMinZoom int        `json:"serve_min_zoom"`
	Bounds       [4]float64 `json:"bounds"`
}

func Load(path string) (Config, error) {
	var cfg Config
	data, err := os.ReadFile(path)
	if err != nil {
		return cfg, fmt.Errorf("read configuration: %w", err)
	}
	if err := json.Unmarshal(data, &cfg); err != nil {
		return cfg, fmt.Errorf("parse configuration: %w", err)
	}
	return cfg, nil
}
