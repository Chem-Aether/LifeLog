import gzip

import mapbox_vector_tile
from shapely.affinity import affine_transform
from shapely.geometry import box, mapping, shape


def _clip_geometry(geometry, clip_box):
    clipped = geometry.intersection(clip_box)
    if clipped.is_empty:
        return None
    if clipped.geom_type != "GeometryCollection":
        return clipped
    parts = [part for part in clipped.geoms if not part.is_empty]
    return parts[0] if len(parts) == 1 else None


def overzoom_mvt(compressed_tile: bytes, delta: int, child_x: int, child_y: int) -> bytes:
    raw = gzip.decompress(compressed_tile)
    decoded = mapbox_vector_tile.decode(raw, default_options={"y_coord_down": True})
    scale = 1 << delta
    layers = []
    layer_options = {}

    for name, layer in decoded.items():
        extent = int(layer.get("extent", 4096))
        clip_box = box(0, 0, extent, extent)
        transform = [scale, 0, 0, scale, -child_x * extent, -child_y * extent]
        features = []
        for feature in layer.get("features", []):
            geometry = affine_transform(shape(feature["geometry"]), transform)
            geometry = _clip_geometry(geometry, clip_box)
            if geometry is None:
                continue
            encoded = {"geometry": mapping(geometry), "properties": feature.get("properties", {})}
            if feature.get("id") is not None:
                encoded["id"] = feature["id"]
            features.append(encoded)
        if features:
            layers.append({"name": name, "features": features})
            layer_options[name] = {"extents": extent, "y_coord_down": True}

    encoded = mapbox_vector_tile.encode(layers, per_layer_options=layer_options) if layers else b""
    return gzip.compress(encoded, compresslevel=5)
