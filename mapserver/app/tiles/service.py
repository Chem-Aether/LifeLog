import math
from functools import lru_cache

from .overzoom import overzoom_mvt
from .registry import TileSourceRegistry


def tile_center(z: int, x: int, y: int) -> tuple[float, float]:
    size = 1 << z
    longitude = (x + 0.5) / size * 360.0 - 180.0
    latitude = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + 0.5) / size))))
    return longitude, latitude


class TileService:
    def __init__(self, registry: TileSourceRegistry, cache_size: int = 512):
        self.registry = registry
        self.get = lru_cache(maxsize=cache_size)(self._get)

    def reload(self) -> None:
        self.get.cache_clear()
        self.registry.reload()

    def _get(self, z: int, x: int, y: int):
        longitude, latitude = tile_center(z, x, y)
        for source in self.registry.candidates(z, longitude, latitude):
            for source_z in range(min(z, source.max_zoom), source.serve_min_zoom - 1, -1):
                delta = z - source_z
                source_x = x >> delta
                source_y = y >> delta
                tile = source.reader.get(source_z, source_x, source_y)
                if tile is None:
                    continue
                if delta == 0:
                    return tile, source.id, False
                child_mask = (1 << delta) - 1
                return overzoom_mvt(tile, delta, x & child_mask, y & child_mask), source.id, True
        return None
