package test

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"lifelog/goserver/internal/app"
	"lifelog/goserver/internal/config"
)

var handler http.Handler

func TestMain(m *testing.M) {
	application, err := app.New("config/application.json")
	if err != nil {
		panic(err)
	}
	handler = application.Handler()
	os.Exit(m.Run())
}

func TestTileEndpoints(t *testing.T) {
	for _, path := range []string{"/api/tiles/tilejson.json", "/api/maps/catalog", "/api/maps/status"} {
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest("GET", path, nil))
		if recorder.Code != http.StatusOK {
			t.Fatalf("%s returned %d: %s", path, recorder.Code, recorder.Body.String())
		}
	}
	assertTile(t, "/api/tiles/0/0/0.mvt", false)
	assertTile(t, "/api/tiles/17/108788/53201.mvt", true)
}

func TestSwaggerDocs(t *testing.T) {
	for _, path := range []string{"/swagger/index.html", "/swagger/doc.json"} {
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest("GET", path, nil))
		if recorder.Code != http.StatusOK {
			t.Fatalf("%s returned %d: %s", path, recorder.Code, recorder.Body.String())
		}
	}
}

func TestAPIDocumentationEnvironmentSettings(t *testing.T) {
	for _, test := range []struct {
		file    string
		enabled bool
	}{
		{file: "application.json", enabled: true},
		{file: "application.production.json", enabled: false},
	} {
		cfg, err := config.Load(filepath.Join("..", "config", test.file))
		if err != nil {
			t.Fatalf("load %s: %v", test.file, err)
		}
		if cfg.APIDocs.Enabled != test.enabled {
			t.Errorf("%s api_docs.enabled = %t, want %t", test.file, cfg.APIDocs.Enabled, test.enabled)
		}
	}
}

func assertTile(t *testing.T, path string, wantOverzoom bool) {
	t.Helper()
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest("GET", path, nil))
	if !wantOverzoom && recorder.Code == http.StatusNoContent {
		return
	}
	if recorder.Code != http.StatusOK {
		t.Fatalf("tile %s returned %d: %s", path, recorder.Code, recorder.Body.String())
	}
	if wantOverzoom && (recorder.Header().Get("X-Map-Source") != "china" || recorder.Header().Get("X-Map-Overzoom") != "true") {
		t.Fatalf("tile %s has unexpected source/overzoom headers: %v", path, recorder.Header())
	}
	reader, err := gzip.NewReader(bytes.NewReader(recorder.Body.Bytes()))
	if err != nil {
		t.Fatalf("tile %s has invalid gzip: %v", path, err)
	}
	decoded, err := io.ReadAll(reader)
	_ = reader.Close()
	if err != nil || len(decoded) == 0 {
		t.Fatalf("tile %s has invalid or empty MVT: %v", path, err)
	}
}

func TestRailwayRouteSampling(t *testing.T) {
	for _, stations := range [][]string{{"西安北", "武汉"}, {"茌平南", "淄博北"}} {
		body, _ := json.Marshal(map[string]any{"stations": stations})
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest("POST", "/api/railway/routes/sample", bytes.NewReader(body))
		request.Header.Set("Content-Type", "application/json")
		handler.ServeHTTP(recorder, request)
		if recorder.Code != http.StatusOK {
			t.Fatalf("%s to %s: status %d: %s", stations[0], stations[1], recorder.Code, recorder.Body.String())
		}
		var envelope struct {
			Data struct {
				RouteSegments []struct {
					Candidates []json.RawMessage `json:"candidates"`
				} `json:"routeSegments"`
			} `json:"data"`
		}
		if err := json.Unmarshal(recorder.Body.Bytes(), &envelope); err != nil || len(envelope.Data.RouteSegments) != 1 || len(envelope.Data.RouteSegments[0].Candidates) == 0 {
			t.Fatalf("%s to %s: expected a matched railway route candidate; response: %s", stations[0], stations[1], recorder.Body.String())
		}
	}
}

func TestLocalDataEndpoints(t *testing.T) {
	tests := []struct{ path, want string }{
		{"/health", `"status":"ok"`},
		{"/api/regions/search?keyword=%E5%8D%97%E4%BA%AC%E5%B8%82&level=2", `"name":"南京市"`},
		{"/api/regions/reverse?longitude=118.7969&latitude=32.0603", `"name":"玄武区"`},
		{"/api/pois/search?q=%E5%A4%A9%E5%AE%89%E9%97%A8", `"name"`},
		{"/api/airports/search?keyword=%E5%8C%97%E4%BA%AC", `"name"`},
		{"/api/railway/stations/search?keyword=%E8%A5%BF%E5%AE%89%E5%8C%97", `"name"`},
	}
	for _, test := range tests {
		t.Run(test.path, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			handler.ServeHTTP(recorder, httptest.NewRequest("GET", test.path, nil))
			if recorder.Code != http.StatusOK {
				t.Fatalf("status %d: %s", recorder.Code, recorder.Body.String())
			}
			if !strings.Contains(recorder.Body.String(), test.want) {
				t.Fatalf("expected %q in response: %s", test.want, recorder.Body.String())
			}
		})
	}
}
