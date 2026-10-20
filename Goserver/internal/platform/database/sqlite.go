package database

import (
	"database/sql"
	"fmt"
	"path/filepath"

	"lifelog/goserver/internal/config"
	_ "modernc.org/sqlite"
)

type Databases struct{ pools map[string]*sql.DB }

func Open(root string, paths config.Storage) (*Databases, error) {
	pools := make(map[string]*sql.DB)
	files := map[string]string{"regions": paths.AdminBoundaries, "poi": paths.POI, "airports": paths.Airports, "network": paths.RailwayNetwork, "routing": paths.RailwayRouting}
	for name, relative := range files {
		path := filepath.Join(root, filepath.FromSlash(relative))
		db, err := sql.Open("sqlite", "file:"+filepath.ToSlash(path)+"?mode=ro")
		if err != nil {
			return nil, fmt.Errorf("open %s database: %w", name, err)
		}
		db.SetMaxOpenConns(8)
		db.SetMaxIdleConns(4)
		if err := db.Ping(); err != nil {
			_ = db.Close()
			return nil, fmt.Errorf("connect %s database at %q: %w", name, path, err)
		}
		pools[name] = db
	}
	return &Databases{pools: pools}, nil
}

func (d *Databases) Get(name string) *sql.DB { return d.pools[name] }

func (d *Databases) Close() error {
	var first error
	for _, db := range d.pools {
		if err := db.Close(); err != nil && first == nil {
			first = err
		}
	}
	return first
}
