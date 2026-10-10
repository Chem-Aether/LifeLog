from shapely import from_wkb


def decode_geometry(blob):
    if not blob or blob[:2] != b"GP":
        raise ValueError("Invalid GeoPackage geometry")
    envelope_sizes = (0, 32, 48, 48, 64)
    envelope = (blob[3] >> 1) & 7
    return from_wkb(blob[8 + envelope_sizes[envelope]:])
