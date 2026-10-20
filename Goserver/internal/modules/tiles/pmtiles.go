package tiles

import (
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"sort"
	"sync"

	"github.com/paulmach/orb"
	"github.com/paulmach/orb/encoding/mvt"
	"lifelog/goserver/internal/config"
)

const tileHeaderSize = 127

type header struct {
	RootOffset, RootLength, MetadataOffset, MetadataLength, LeafOffset, LeafLength, TileOffset, TileLength uint64
	InternalCompression, TileCompression, MetadataCompression                                              uint8
	MinZoom, MaxZoom                                                                                       uint8
	MinLon, MinLat, MaxLon, MaxLat                                                                         int32
}
type source struct {
	id, path, name                       string
	priority, minZoom, maxZoom, serveMin int
	bounds                               [4]float64
	file                                 *os.File
	header                               header
	mu                                   sync.Mutex
	root                                 []entry
}
type entry struct {
	tileID         uint64
	offset, length uint64
	run            uint64
}
type Registry struct {
	root                   string
	configs                []config.TileSource
	sources                []*source
	mu                     sync.RWMutex
	cacheMu                sync.Mutex
	cache                  map[tileKey]tileResult
	cacheKeys              []tileKey
	cacheSize              int
	cacheHits, cacheMisses uint64
}

type tileKey struct{ z, x, y int }
type tileResult struct {
	data       []byte
	source     string
	overzoomed bool
}

func NewRegistry(root string, configs []config.TileSource, cacheSize int) (*Registry, error) {
	if cacheSize < 1 {
		cacheSize = 512
	}
	r := &Registry{root: root, configs: configs, cache: make(map[tileKey]tileResult), cacheSize: cacheSize}
	if err := r.Reload(); err != nil {
		return nil, err
	}
	return r, nil
}
func (r *Registry) Reload() error {
	loaded := make([]*source, 0, len(r.configs))
	for _, cfg := range r.configs {
		path := filepath.Join(r.root, cfg.File)
		f, err := os.Open(path)
		if err != nil {
			for _, s := range loaded {
				_ = s.file.Close()
			}
			return fmt.Errorf("open PMTiles %s: %w", path, err)
		}
		h, err := readHeader(f)
		if err != nil {
			_ = f.Close()
			return fmt.Errorf("read PMTiles %s: %w", path, err)
		}
		root, err := readDirectory(f, h.RootOffset, h.RootLength, h.InternalCompression)
		if err != nil {
			_ = f.Close()
			return err
		}
		name := cfg.ID
		if h.MetadataLength > 0 && h.MetadataLength <= 4*1024*1024 {
			metadataBytes := make([]byte, h.MetadataLength)
			if _, err := f.ReadAt(metadataBytes, int64(h.MetadataOffset)); err == nil {
				if raw, err := decompress(metadataBytes, h.MetadataCompression); err == nil {
					var metadata map[string]any
					if json.Unmarshal(raw, &metadata) == nil {
						if metadataName, ok := metadata["name"].(string); ok && metadataName != "" {
							name = metadataName
						}
					}
				}
			}
		}
		bounds := cfg.Bounds
		if bounds == [4]float64{} {
			bounds = [4]float64{float64(h.MinLon) / 1e7, float64(h.MinLat) / 1e7, float64(h.MaxLon) / 1e7, float64(h.MaxLat) / 1e7}
		}
		loaded = append(loaded, &source{id: cfg.ID, path: path, name: name, priority: cfg.Priority, serveMin: cfg.ServeMinZoom, minZoom: int(h.MinZoom), maxZoom: int(h.MaxZoom), bounds: bounds, file: f, header: h, root: root})
	}
	sort.Slice(loaded, func(i, j int) bool { return loaded[i].priority > loaded[j].priority })
	r.mu.Lock()
	old := r.sources
	r.sources = loaded
	r.mu.Unlock()
	for _, s := range old {
		_ = s.file.Close()
	}
	r.cacheMu.Lock()
	r.cache = make(map[tileKey]tileResult)
	r.cacheKeys = nil
	r.cacheHits, r.cacheMisses = 0, 0
	r.cacheMu.Unlock()
	return nil
}
func readHeader(f *os.File) (header, error) {
	var h header
	b := make([]byte, tileHeaderSize)
	if _, err := f.ReadAt(b, 0); err != nil {
		return h, err
	}
	if string(b[:7]) != "PMTiles" || b[7] != 3 {
		return h, errors.New("unsupported PMTiles header")
	}
	u := func(i int) uint64 { return binary.LittleEndian.Uint64(b[i : i+8]) }
	h = header{RootOffset: u(8), RootLength: u(16), MetadataOffset: u(24), MetadataLength: u(32), LeafOffset: u(40), LeafLength: u(48), TileOffset: u(56), TileLength: u(64), InternalCompression: b[97], TileCompression: b[98], MetadataCompression: b[99], MinZoom: b[100], MaxZoom: b[101], MinLon: int32(binary.LittleEndian.Uint32(b[102:106])), MinLat: int32(binary.LittleEndian.Uint32(b[106:110])), MaxLon: int32(binary.LittleEndian.Uint32(b[110:114])), MaxLat: int32(binary.LittleEndian.Uint32(b[114:118]))}
	return h, nil
}
func decompress(b []byte, kind uint8) ([]byte, error) {
	if kind == 1 {
		return b, nil
	}
	if kind != 2 {
		return nil, fmt.Errorf("unsupported PMTiles compression %d", kind)
	}
	z, err := gzip.NewReader(bytes.NewReader(b))
	if err != nil {
		return nil, err
	}
	defer z.Close()
	return io.ReadAll(z)
}
func readDirectory(f *os.File, off, n uint64, compression uint8) ([]entry, error) {
	b := make([]byte, n)
	if _, err := f.ReadAt(b, int64(off)); err != nil {
		return nil, err
	}
	raw, err := decompress(b, compression)
	if err != nil {
		return nil, err
	}
	idx := 0
	read := func() (uint64, error) {
		if idx >= len(raw) {
			return 0, errors.New("truncated PMTiles directory")
		}
		v, k := binary.Uvarint(raw[idx:])
		if k <= 0 {
			return 0, errors.New("invalid PMTiles directory varint")
		}
		idx += k
		return v, nil
	}
	count, err := read()
	if err != nil {
		return nil, err
	}
	if count > 10_000_000 {
		return nil, errors.New("PMTiles directory entry limit exceeded")
	}
	out := make([]entry, count)
	var last uint64
	for i := range out {
		d, e := read()
		if e != nil {
			return nil, e
		}
		last += d
		out[i].tileID = last
	}
	for i := range out {
		v, e := read()
		if e != nil {
			return nil, e
		}
		out[i].run = v
	}
	for i := range out {
		v, e := read()
		if e != nil {
			return nil, e
		}
		out[i].length = v
	}
	var end uint64
	for i := range out {
		v, e := read()
		if e != nil {
			return nil, e
		}
		if v == 0 {
			out[i].offset = end
		} else {
			out[i].offset = v - 1
		}
		end = out[i].offset + out[i].length
	}
	return out, nil
}
func tileID(z, x, y int) uint64 {
	if z == 0 {
		return 0
	}
	acc := (uint64(1)<<(2*z) - 1) / 3
	n := uint(z - 1)
	xv, yv := uint32(x), uint32(y)
	for size := uint32(1 << n); size > 0; size >>= 1 {
		rx, ry := size&xv, size&yv
		acc += uint64((3*rx)^ry) << n
		if ry == 0 {
			if rx != 0 {
				xv, yv = size-1-xv, size-1-yv
			}
			xv, yv = yv, xv
		}
		n--
	}
	return acc
}
func findEntry(entries []entry, id uint64) (entry, bool) {
	i := sort.Search(len(entries), func(i int) bool { return entries[i].tileID > id }) - 1
	if i < 0 {
		return entry{}, false
	}
	e := entries[i]
	if e.run == 0 {
		return e, true
	}
	if id < e.tileID+e.run {
		return entry{offset: e.offset + (id-e.tileID)*e.length, length: e.length, run: 1}, true
	}
	return entry{}, false
}
func (s *source) get(z, x, y int) ([]byte, error) {
	id := tileID(z, x, y)
	s.mu.Lock()
	defer s.mu.Unlock()
	entries := s.root
	for depth := 0; depth < 4; depth++ {
		e, ok := findEntry(entries, id)
		if !ok {
			return nil, nil
		}
		if e.run > 0 {
			b := make([]byte, e.length)
			_, err := s.file.ReadAt(b, int64(s.header.TileOffset+e.offset))
			return b, err
		}
		next, err := readDirectory(s.file, s.header.LeafOffset+e.offset, e.length, s.header.InternalCompression)
		if err != nil {
			return nil, err
		}
		entries = next
	}
	return nil, errors.New("PMTiles leaf directory nesting too deep")
}
func (r *Registry) Sources() []map[string]any {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]map[string]any, 0, len(r.sources))
	for _, s := range r.sources {
		out = append(out, map[string]any{"id": s.id, "file": filepath.Base(s.path), "priority": s.priority, "serveMinZoom": s.serveMin, "minZoom": s.minZoom, "maxZoom": s.maxZoom, "bounds": s.bounds, "name": s.name})
	}
	return out
}
func (r *Registry) Tile(z, x, y int) ([]byte, string, bool, error) {
	key := tileKey{z, x, y}
	r.cacheMu.Lock()
	if value, ok := r.cache[key]; ok {
		r.cacheHits++
		r.cacheMu.Unlock()
		return value.data, value.source, value.overzoomed, nil
	}
	r.cacheMisses++
	r.cacheMu.Unlock()
	data, source, overzoomed, err := r.readTile(z, x, y)
	if err == nil {
		r.cacheMu.Lock()
		if _, ok := r.cache[key]; !ok {
			r.cache[key] = tileResult{data, source, overzoomed}
			r.cacheKeys = append(r.cacheKeys, key)
			if len(r.cacheKeys) > r.cacheSize {
				old := r.cacheKeys[0]
				r.cacheKeys = r.cacheKeys[1:]
				delete(r.cache, old)
			}
		}
		r.cacheMu.Unlock()
	}
	return data, source, overzoomed, err
}
func (r *Registry) CacheInfo() map[string]any {
	r.cacheMu.Lock()
	defer r.cacheMu.Unlock()
	return map[string]any{"hits": r.cacheHits, "misses": r.cacheMisses, "maxsize": r.cacheSize, "currsize": len(r.cache)}
}
func (r *Registry) readTile(z, x, y int) ([]byte, string, bool, error) {
	lon := (float64(x)+.5)/math.Exp2(float64(z))*360 - 180
	lat := math.Atan(math.Sinh(math.Pi*(1-2*(float64(y)+.5)/math.Exp2(float64(z))))) * 180 / math.Pi
	r.mu.RLock()
	defer r.mu.RUnlock()
	for _, s := range r.sources {
		if z < s.serveMin || lon < s.bounds[0] || lon > s.bounds[2] || lat < s.bounds[1] || lat > s.bounds[3] {
			continue
		}
		sourceZoom := min(z, s.maxZoom)
		for sourceZoom >= s.serveMin {
			delta := z - sourceZoom
			sx, sy := x>>delta, y>>delta
			b, err := s.get(sourceZoom, sx, sy)
			if err != nil {
				return nil, "", false, err
			}
			if len(b) > 0 {
				if delta == 0 {
					return b, s.id, false, nil
				}
				childMask := (1 << delta) - 1
				transformed, err := overzoom(b, delta, x&childMask, y&childMask)
				return transformed, s.id, true, err
			}
			sourceZoom--
		}
	}
	return nil, "", false, nil
}
func overzoom(data []byte, delta, cx, cy int) ([]byte, error) {
	layers, err := mvt.UnmarshalGzipped(data)
	if err != nil {
		return nil, err
	}
	scale := float64(uint64(1) << delta)
	for _, layer := range layers {
		extent := float64(layer.Extent)
		for _, feature := range layer.Features {
			feature.Geometry = transformGeometry(feature.Geometry, scale, extent*float64(cx), extent*float64(cy))
		}
	}
	return mvt.MarshalGzipped(layers)
}
func transformPoint(p orb.Point, s, dx, dy float64) orb.Point {
	return orb.Point{p[0]*s - dx, p[1]*s - dy}
}
func transformGeometry(g orb.Geometry, s, dx, dy float64) orb.Geometry {
	switch v := g.(type) {
	case orb.Point:
		return transformPoint(v, s, dx, dy)
	case orb.MultiPoint:
		r := make(orb.MultiPoint, len(v))
		for i, p := range v {
			r[i] = transformPoint(p, s, dx, dy)
		}
		return r
	case orb.LineString:
		r := make(orb.LineString, len(v))
		for i, p := range v {
			r[i] = transformPoint(p, s, dx, dy)
		}
		return r
	case orb.MultiLineString:
		r := make(orb.MultiLineString, len(v))
		for i, line := range v {
			r[i] = transformGeometry(line, s, dx, dy).(orb.LineString)
		}
		return r
	case orb.Ring:
		r := make(orb.Ring, len(v))
		for i, p := range v {
			r[i] = transformPoint(p, s, dx, dy)
		}
		return r
	case orb.Polygon:
		r := make(orb.Polygon, len(v))
		for i, ring := range v {
			r[i] = transformGeometry(ring, s, dx, dy).(orb.Ring)
		}
		return r
	case orb.MultiPolygon:
		r := make(orb.MultiPolygon, len(v))
		for i, poly := range v {
			r[i] = transformGeometry(poly, s, dx, dy).(orb.Polygon)
		}
		return r
	case orb.Collection:
		r := make(orb.Collection, len(v))
		for i, x := range v {
			r[i] = transformGeometry(x, s, dx, dy)
		}
		return r
	default:
		return g
	}
}
