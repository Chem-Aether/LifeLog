package geospatial

import (
	"errors"

	"github.com/paulmach/orb"
	"github.com/paulmach/orb/encoding/wkb"
)

func DecodeGeoPackage(data []byte) (orb.Geometry, error) {
	if len(data) < 8 || data[0] != 'G' || data[1] != 'P' {
		return nil, errors.New("invalid GeoPackage geometry")
	}
	flags := data[3]
	envelopeBytes := [...]int{0, 32, 48, 48, 64}[flags>>1&7]
	start := 8 + envelopeBytes
	if start > len(data) {
		return nil, errors.New("invalid GeoPackage envelope")
	}
	return wkb.Unmarshal(data[start:])
}

func GeometryJSON(data []byte) any {
	geometry, err := DecodeGeoPackage(data)
	if err != nil {
		return nil
	}
	return map[string]any{"type": geometry.GeoJSONType(), "coordinates": geometry}
}

func pointInRing(point orb.Point, ring orb.Ring) bool {
	inside := false
	for i, j := 0, len(ring)-1; i < len(ring); j, i = i, i+1 {
		a, b := ring[i], ring[j]
		if (a[1] > point[1]) != (b[1] > point[1]) && point[0] < (b[0]-a[0])*(point[1]-a[1])/(b[1]-a[1])+a[0] {
			inside = !inside
		}
	}
	return inside
}

func GeometryContains(g orb.Geometry, point orb.Point) bool {
	switch shape := g.(type) {
	case orb.Polygon:
		if len(shape) == 0 || !pointInRing(point, shape[0]) {
			return false
		}
		for _, hole := range shape[1:] {
			if pointInRing(point, hole) {
				return false
			}
		}
		return true
	case orb.MultiPolygon:
		for _, polygon := range shape {
			if GeometryContains(polygon, point) {
				return true
			}
		}
	}
	return false
}
