# LifeLog Go Map Service

Gin implementation of the existing `mapserver`. It runs independently and does not modify or replace the Python service. Its data is an independent local copy in this project's `data` directory. Runtime settings are stored in `config/application.json`; no environment variables, cloud storage, or cloud services are required.

## Structure

```text
cmd/goserver/                 executable entry point
config/                       application configuration
data/                         independent read-only data copy
internal/app/                 application bootstrap and dependency wiring
internal/config/              configuration model and loader
internal/modules/catalog/     POI and airport search/statistics module
internal/modules/railway/     station search and route sampling module
internal/modules/regions/     administrative-area search/reverse/boundary module
internal/modules/tiles/       local PMTiles reader, overzoom and tile cache
internal/platform/database/   SQLite/GeoPackage connections
internal/platform/geospatial/ GeoPackage geometry decoding and predicates
internal/transport/http/      Gin server, middleware and route registration
test/                         local-data integration tests
internal/transport/response/  shared API response format
```

The HTTP transport only wires business modules and serves map tiles. Read-only spatial databases and local PMTiles remain under `data/`; 12306 map-line lookup is an optional HTTP integration with the separately configured SpyderServer, followed by local OSM route search when unavailable.

## Development

```powershell
cd Goserver
go run ./cmd/goserver
```

The service listens on `127.0.0.1:8766`, leaving the Python service on `8765` untouched. All GeoPackage and PMTiles files are read from `Goserver/data`.

Build a standalone executable with `go build -o Goserver.exe ./cmd/goserver`; keep `config/application.json` and the `data/` directory beside it. The databases and PMTiles are read-only at runtime.

The migration target is API compatibility with the existing service. Switch clients only after endpoint parity and tile/railway result comparisons pass.
