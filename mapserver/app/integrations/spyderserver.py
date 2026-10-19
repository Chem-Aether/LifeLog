import json
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import urlopen

from ..settings import SPYDERSERVER_BASE_URL


def get_train_map_line(train_code: str, timeout: float = 12.0) -> dict | None:
    """Fetch a normalized train map line without coupling to spider internals."""
    url = f"{SPYDERSERVER_BASE_URL}/trains/{quote(train_code, safe='')}/map-line"
    try:
        with urlopen(url, timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
    except (HTTPError, URLError, TimeoutError, OSError, ValueError):
        return None

    data = body.get("data") if isinstance(body, dict) else None
    geometry = data.get("geometry") if isinstance(data, dict) else None
    coordinates = geometry.get("coordinates") if isinstance(geometry, dict) else None
    if not isinstance(geometry, dict) or geometry.get("type") != "LineString":
        return None
    if not isinstance(coordinates, list) or len(coordinates) < 2:
        return None
    if any(
        not isinstance(point, list)
        or len(point) < 2
        or not all(isinstance(value, (int, float)) for value in point[:2])
        for point in coordinates
    ):
        return None
    return {"geometry": geometry, "train_no": data.get("train_no")}
