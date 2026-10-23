# LifeLog Go Map Service

Gin implementation of the existing `mapserver`. It runs independently and does not modify or replace the Python service. Its data is an independent local copy in this project's `data` directory. Runtime settings are stored in `config/application.json`; no environment variables, cloud storage, or cloud services are required.

## Structure

```text
cmd/goserver/                 executable entry point
config/                       application configuration
data/                         independent read-only data copy
internal/app/                 application bootstrap and dependency wiring
internal/config/              configuration model and loader
internal/modules/catalog/     POI search and statistics module
internal/modules/airports/     airport search and code lookup module
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
go run ./cmd/goserver -config config/application.json
```

The development configuration enables Swagger UI at `http://127.0.0.1:8766/swagger/index.html`; the OpenAPI JSON is available at `/swagger/doc.json`. Regenerate the API specification after changing endpoint annotations:

```powershell
swag init -g cmd/goserver/main.go --parseInternal
```

The service listens on `127.0.0.1:8766`, leaving the Python service on `8765` untouched. All GeoPackage and PMTiles files are read from `Goserver/data`.

## Production

Production configuration disables API documentation. Start the service with:

```powershell
go build -o Goserver.exe ./cmd/goserver
.\Goserver.exe -config config/application.production.json
```

The service returns `404` for `/swagger/*` when documentation is disabled. Keep the selected config file and `data/` directory beside the executable. The databases and PMTiles are read-only at runtime.

The migration target is API compatibility with the existing service. Switch clients only after endpoint parity and tile/railway result comparisons pass.
