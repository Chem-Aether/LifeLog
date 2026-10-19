import math
from datetime import datetime, timedelta, timezone

import requests


TRAIN_DETAIL_URL = "https://mobile.12306.cn/wxxcx/wechat/main/travelServiceQrcodeTrainInfo"
TRAIN_MAP_LINE_URL = "https://mobile.12306.cn/wxxcx/wechat/main/getTrainMapLine"
TRAINSET_DETAIL_URL = (
    "https://mobile.12306.cn/wxxcx/openplatform-inner/miniprogram/wifiapps/"
    "appFrontEnd/v2/lounge/open-smooth-common/qrCode/getDeptByTrainCode"
)
TRAIN_SEARCH_URL = "https://search.12306.cn/search/v1/train/search"
TRAIN_QUERY_URL = "https://kyfw.12306.cn/otn/queryTrainInfo/query"
TRAIN_QUERY_INIT_URL = "https://kyfw.12306.cn/otn/queryTrainInfo/init"

_GCJ_A = 6378245.0
_GCJ_EE = 0.00669342162296594323
_SHANGHAI_TIMEZONE = timezone(timedelta(hours=8))

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    ),
    "Referer": "https://mobile.12306.cn/",
    "Accept": "application/json, text/plain, */*",
}


def make_session():
    """Create an isolated HTTP session using the platform's normal TLS validation."""
    session = requests.Session()
    session.headers.update(HEADERS)
    return session


def today_yyyymmdd():
    """Return today's date in the timezone used by 12306 train services."""
    return datetime.now(_SHANGHAI_TIMEZONE).strftime("%Y%m%d")


def build_datetime(base_date, time_str, day_diff=0):
    """Convert a service date, clock time, and day offset to an ISO datetime."""
    if not time_str or time_str == "----":
        return None
    try:
        base = datetime.strptime(base_date, "%Y%m%d")
        hour, minute = map(int, time_str.split(":")[:2])
        return (base.replace(hour=hour, minute=minute) + timedelta(days=int(day_diff or 0))).isoformat()
    except (ValueError, TypeError):
        return None


def _json(response):
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, dict):
        raise ValueError("12306 返回了无效数据")
    return data


def parse_mobile_train_metadata(data):
    """Extract supplementary model and operator metadata from the mini-program response."""
    detail = (data.get("data") or {}).get("trainDetail") or {}
    raw_stations = detail.get("stopTime") or []
    trainset = detail.get("trainsetTypeInfo") or {}
    first_stop = raw_stations[0] if raw_stations else {}
    return {
        "train_model": trainset.get("trainsetTypeName"),
        "train_owner": first_stop.get("jiaolu_dept_train"),
        "train_runner": first_stop.get("jiaolu_corporation_code"),
    }


def _query_mobile_train_metadata(session, train_code, base_date):
    data = _json(session.post(
        TRAIN_DETAIL_URL,
        data={"trainCode": train_code, "startDay": base_date},
        timeout=12,
    ))
    return parse_mobile_train_metadata(data)


def parse_train_map_line(data):
    """Normalize the 12306 map-line groups into a GeoJSON LineString."""
    raw_groups = data.get("data") or {}
    if not isinstance(raw_groups, dict):
        return None

    coordinates = []
    for group in raw_groups.values():
        if not isinstance(group, dict):
            continue
        for point in group.get("line") or []:
            coordinate = _map_line_point(point)
            if coordinate and (not coordinates or coordinate != coordinates[-1]):
                coordinates.append(coordinate)

    if len(coordinates) < 2:
        return None
    return {"type": "LineString", "coordinates": coordinates}


def _map_line_point(point):
    if isinstance(point, dict):
        longitude = next((point[key] for key in ("longitude", "lng", "lon", "x") if point.get(key) is not None), None)
        latitude = next((point[key] for key in ("latitude", "lat", "y") if point.get(key) is not None), None)
        if longitude is None or latitude is None:
            return None
    elif isinstance(point, (list, tuple)) and len(point) >= 2:
        longitude, latitude = point[:2]
    else:
        return None

    try:
        longitude, latitude = float(longitude), float(latitude)
    except (TypeError, ValueError):
        return None
    if not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
        return None
    return list(gcj02_to_wgs84(longitude, latitude))


def gcj02_to_wgs84(longitude, latitude):
    """Convert a GCJ-02 point from 12306 to WGS-84 for OSM map alignment."""
    transformed_longitude, transformed_latitude = longitude, latitude
    for _ in range(8):
        offset_longitude, offset_latitude = _gcj02_offset(transformed_longitude, transformed_latitude)
        corrected_longitude = longitude - offset_longitude
        corrected_latitude = latitude - offset_latitude
        if abs(corrected_longitude - transformed_longitude) < 1e-7 and abs(
            corrected_latitude - transformed_latitude
        ) < 1e-7:
            transformed_longitude, transformed_latitude = corrected_longitude, corrected_latitude
            break
        transformed_longitude, transformed_latitude = corrected_longitude, corrected_latitude
    return transformed_longitude, transformed_latitude


def _gcj02_offset(longitude, latitude):
    x, y = longitude - 105.0, latitude - 35.0
    latitude_offset = _transform_latitude(x, y)
    longitude_offset = _transform_longitude(x, y)
    radians = latitude / 180.0 * 3.141592653589793
    magic = 1 - _GCJ_EE * math.sin(radians) ** 2
    sqrt_magic = magic ** 0.5
    latitude_offset = latitude_offset * 180.0 / (
        (_GCJ_A * (1 - _GCJ_EE)) / (magic * sqrt_magic) * math.pi
    )
    longitude_offset = longitude_offset * 180.0 / (
        _GCJ_A / sqrt_magic * math.cos(radians) * math.pi
    )
    return longitude_offset, latitude_offset


def _transform_latitude(x, y):
    value = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
    value += (20 * math.sin(6 * x * math.pi) + 20 * math.sin(2 * x * math.pi)) * 2 / 3
    value += (20 * math.sin(y * math.pi) + 40 * math.sin(y / 3 * math.pi)) * 2 / 3
    value += (160 * math.sin(y / 12 * math.pi) + 320 * math.sin(y * math.pi / 30)) * 2 / 3
    return value


def _transform_longitude(x, y):
    value = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
    value += (20 * math.sin(6 * x * math.pi) + 20 * math.sin(2 * x * math.pi)) * 2 / 3
    value += (20 * math.sin(x * math.pi) + 40 * math.sin(x / 3 * math.pi)) * 2 / 3
    value += (150 * math.sin(x / 12 * math.pi) + 300 * math.sin(x / 30 * math.pi)) * 2 / 3
    return value


def get_train_map_line(train_code):
    """Fetch the train's 12306 map geometry by its internal train identifier."""
    date_yyyymmdd = today_yyyymmdd()
    datetime.strptime(date_yyyymmdd, "%Y%m%d")
    session = make_session()
    train_no = get_train_no(session, train_code, date_yyyymmdd)
    if not train_no:
        return None

    data = _json(session.post(
        TRAIN_MAP_LINE_URL,
        data={"version": "v2", "trainNo": train_no},
        timeout=12,
    ))
    geometry = parse_train_map_line(data)
    if not geometry:
        return None
    return {
        "train_code": train_code,
        "date": date_yyyymmdd,
        "train_no": train_no,
        "geometry": geometry,
    }


def _query_trainset_detail(session, train_code):
    """Fetch supplementary operator/model data; this endpoint is best-effort."""
    try:
        data = _json(session.post(
            TRAINSET_DETAIL_URL,
            data={"trainCode": train_code, "reqType": "form"},
            timeout=8,
        ))
        content = data.get("content") or {}
        result = content.get("data") or {}
        car_info = result.get("carInfo") or {}
        return {
            "train_owner": result.get("deptName"),
            "train_model": car_info.get("trainStyle"),
        }
    except (requests.RequestException, ValueError, TypeError):
        return {}


def get_train_no(session, train_code, date_yyyymmdd):
    data = _json(session.get(
        TRAIN_SEARCH_URL,
        params={"keyword": train_code, "date": date_yyyymmdd},
        timeout=8,
    ))
    target = train_code.upper()
    for item in data.get("data") or []:
        if item.get("station_train_code", "").upper() == target:
            return item.get("train_no")
    return None


def query_route(session, train_no, date_yyyymmdd):
    date = f"{date_yyyymmdd[:4]}-{date_yyyymmdd[4:6]}-{date_yyyymmdd[6:8]}"
    session.get(TRAIN_QUERY_INIT_URL, timeout=8)
    data = _json(session.get(
        TRAIN_QUERY_URL,
        params={
            "leftTicketDTO.train_no": train_no,
            "leftTicketDTO.train_date": date,
            "rand_code": "",
        },
        timeout=8,
    ))
    return (data.get("data") or {}).get("data") or []


def _parse_legacy_station(raw, index, base_date):
    day_diff = raw.get("arrive_day_diff", 0)
    arrive_time = raw.get("arrive_time")
    depart_time = raw.get("start_time")
    if arrive_time == "----":
        arrive_time = depart_time
    try:
        day_diff = int(day_diff or 0)
    except (ValueError, TypeError):
        day_diff = 0
    return {
        "order": int(raw.get("station_no") or index),
        "name": raw.get("station_name"),
        "arrive": build_datetime(base_date, arrive_time, day_diff),
        "depart": build_datetime(base_date, depart_time, day_diff),
    }


def get_train_route(train_code):
    """Query the verified OTN timetable and enrich it with mini-program train metadata."""
    date_yyyymmdd = today_yyyymmdd()
    datetime.strptime(date_yyyymmdd, "%Y%m%d")
    session = make_session()

    train_no = get_train_no(session, train_code, date_yyyymmdd)
    if not train_no:
        return None
    raw_stations = query_route(session, train_no, date_yyyymmdd)
    stations = [
        _parse_legacy_station(raw, index, date_yyyymmdd)
        for index, raw in enumerate(raw_stations, start=1)
        if isinstance(raw, dict) and raw.get("station_name")
    ]
    if len(stations) < 2:
        return None

    result = {
        "train_code": train_code,
        "date": date_yyyymmdd,
        "train_no": train_no,
        "train_type": raw_stations[0].get("train_class_name") if raw_stations else None,
        "train_model": None,
        "train_owner": None,
        "train_runner": None,
        "start": stations[0]["name"],
        "end": stations[-1]["name"],
        "stations": stations,
    }

    try:
        metadata = _query_mobile_train_metadata(session, train_code, date_yyyymmdd)
    except (requests.RequestException, ValueError, TypeError, KeyError):
        metadata = {}
    if not metadata.get("train_model") or not metadata.get("train_owner"):
        metadata.update({
            key: value for key, value in _query_trainset_detail(session, train_code).items()
            if value and not metadata.get(key)
        })
    result.update({key: value for key, value in metadata.items() if value})
    return result
