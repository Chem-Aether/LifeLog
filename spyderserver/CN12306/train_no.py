import os
import json
import requests
import urllib3
from datetime import datetime, timedelta

urllib3.disable_warnings()

for k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "ALL_PROXY", "all_proxy"):
    os.environ.pop(k, None)

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Referer": "https://kyfw.12306.cn/otn/queryTrainInfo/init",
    "Accept": "application/json, text/javascript, */*; q=0.01",
}


def make_session():
    s = requests.Session()
    s.trust_env = False
    s.proxies = {}
    s.verify = False
    s.headers.update(HEADERS)
    return s


def get_train_no(session, train_code, date_yyyymmdd):
    r = session.get(
        "https://search.12306.cn/search/v1/train/search",
        params={"keyword": train_code, "date": date_yyyymmdd},
        timeout=8,
    )
    r.raise_for_status()
    data = r.json().get("data") or []
    target = train_code.upper()
    for item in data:
        if item.get("station_train_code", "").upper() == target:
            return item["train_no"]
    return None


def query_route(session, train_no, date_yyyymmdd):
    d = f"{date_yyyymmdd[:4]}-{date_yyyymmdd[4:6]}-{date_yyyymmdd[6:8]}"
    session.get("https://kyfw.12306.cn/otn/queryTrainInfo/init", timeout=8)
    r = session.get(
        "https://kyfw.12306.cn/otn/queryTrainInfo/query",
        params={
            "leftTicketDTO.train_no": train_no,
            "leftTicketDTO.train_date": d,
            "rand_code": "",
        },
        timeout=8,
    )
    if "error.html" in str(r.url):
        print("[被拦截]", r.url)
        return []
    return r.json().get("data", {}).get("data") or []


def build_datetime(base_date, time_str, day_diff):
    """日期 + 时间 + 跨天 → datetime"""
    if not time_str or time_str == "----":
        return None
    try:
        base = datetime.strptime(base_date, "%Y%m%d")
        hh, mm = map(int, time_str.split(":"))
        dt = base.replace(hour=hh, minute=mm, second=0)
        return dt + timedelta(days=day_diff)
    except (ValueError, TypeError):
        return None


def parse_station(raw, train_code, base_date):
    try:
        day_diff = int(raw.get("arrive_day_diff", 0) or 0)
    except (ValueError, TypeError):
        day_diff = 0

    try:
        station_no = int(raw.get("station_no", 0) or 0)
    except (ValueError, TypeError):
        station_no = 0

    arrive_str = raw.get("arrive_time")
    depart_str = raw.get("start_time")

    # 始发站 arrive_time 为 "----"，用出发时间当到达时间
    arrive_dt = build_datetime(base_date, arrive_str if arrive_str != "----" else depart_str, day_diff)
    depart_dt = build_datetime(base_date, depart_str, day_diff)

    return {
        "order": station_no,
        "name": raw.get("station_name"),
        "arrive": arrive_dt.isoformat() if arrive_dt else None,
        "depart": depart_dt.isoformat() if depart_dt else None,
    }


def get_train_route(train_code, date_yyyymmdd):
    session = make_session()

    train_no = get_train_no(session, train_code, date_yyyymmdd)
    if not train_no:
        print(f"[错误] 未找到车次 {train_code}")
        return None

    raw_stations = query_route(session, train_no, date_yyyymmdd)
    if not raw_stations:
        print(f"[错误] 车次 {train_code} 无经停站数据")
        return None

    stations = [parse_station(s, train_code, date_yyyymmdd) for s in raw_stations]

    return {
        "train_code": train_code,
        "date": date_yyyymmdd,
        "train_no": train_no,
        "train_type": raw_stations[0].get("train_class_name"),
        "start": stations[0]["name"] if stations else None,
        "end": stations[-1]["name"] if stations else None,
        "stations": stations,
    }

