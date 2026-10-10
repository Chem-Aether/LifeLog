from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from pmtiles.reader import MmapSource, Reader


@dataclass
class TileSource:
    id: str
    path: Path
    priority: int
    serve_min_zoom: int
    bounds: tuple[float, float, float, float]
    reader: Reader
    file_handle: object
    min_zoom: int
    max_zoom: int
    metadata: dict

    def contains(self, longitude: float, latitude: float) -> bool:
        west, south, east, north = self.bounds
        return west <= longitude <= east and south <= latitude <= north


class TileSourceRegistry:
    def __init__(self, config_loader: Callable[[], list[dict]], data_dir: Path):
        self.config_loader = config_loader
        self.data_dir = data_dir
        self.sources: list[TileSource] = []
        self.reload()

    def reload(self) -> None:
        loaded = []
        for item in self.config_loader():
            path = (self.data_dir / item["file"]).resolve()
            if not path.is_file():
                continue
            handle = path.open("rb")
            reader = Reader(MmapSource(handle))
            header = reader.header()
            metadata = reader.metadata()
            header_bounds = (
                header["min_lon_e7"] / 10_000_000,
                header["min_lat_e7"] / 10_000_000,
                header["max_lon_e7"] / 10_000_000,
                header["max_lat_e7"] / 10_000_000,
            )
            loaded.append(TileSource(
                id=item["id"],
                path=path,
                priority=int(item.get("priority", 0)),
                serve_min_zoom=int(item.get("serve_min_zoom", header["min_zoom"])),
                bounds=tuple(item.get("bounds", header_bounds)),
                reader=reader,
                file_handle=handle,
                min_zoom=header["min_zoom"],
                max_zoom=header["max_zoom"],
                metadata=metadata,
            ))
        old_sources = self.sources
        self.sources = sorted(loaded, key=lambda source: source.priority, reverse=True)
        for source in old_sources:
            source.file_handle.close()

    def candidates(self, z: int, longitude: float, latitude: float) -> list[TileSource]:
        return [
            source for source in self.sources
            if z >= source.serve_min_zoom and source.contains(longitude, latitude)
        ]

    def catalog(self) -> list[dict]:
        return [{
            "id": source.id,
            "file": source.path.name,
            "priority": source.priority,
            "serveMinZoom": source.serve_min_zoom,
            "minZoom": source.min_zoom,
            "maxZoom": source.max_zoom,
            "bounds": source.bounds,
            "name": source.metadata.get("name", source.id),
        } for source in self.sources]
