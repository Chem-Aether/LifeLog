import os
import re
import json
import requests
import urllib3

urllib3.disable_warnings()

# 禁用代理
for k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "ALL_PROXY", "all_proxy"):
    os.environ.pop(k, None)

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Referer": "https://kyfw.12306.cn/otn/leftTicket/init",
}

url = f"https://kyfw.12306.cn/otn/resources/js/framework/station_name.js?station_version=1.9374"

def fetch_station_names():
    """下载 12306 车站字典 JS 文件"""
    session = requests.Session()
    session.trust_env = False
    session.proxies = {}
    session.verify = False
    session.headers.update(HEADERS)

    r = session.get(url, timeout=10)
    r.raise_for_status()
    r.encoding = "utf-8"
    return r.text


def parse_station_names():
    """
    解析 station_name.js

    格式：
    var station_names = '@bjb|北京北|VAP|beijingbei|bjb|0|0357|北京|||@bjd|...';

    字段（用 | 分隔）：
    0: 简拼
    1: 站名
    2: 电报码
    3: 全拼
    4: 简拼（重复）
    5: 序号
    6: 城市代码
    7: 城市名
    8: 国家代码（可选）
    9: 国家名（可选）
    10: 备注（可选）
    """
    text = fetch_station_names()
    
    match = re.search(r"@(.+)", text, re.S)
    if not match:
        return []

    raw = match.group(1)
    stations = []

    for item in raw.split("@"):
        if not item.strip():
            continue
        fields = item.split("|")
        if len(fields) < 8:
            continue

        stations.append({
            "simple_pinyin": fields[0],       # 简拼
            "name": fields[1],                # 站名
            "code": fields[2],                # 电报码
            "pinyin": fields[3],              # 全拼
            "index": fields[5],               # 序号
            "city_code": fields[6],           # 城市代码
            "city": fields[7],                # 城市名
            "country_code": fields[8] if len(fields) > 8 else "",
            "country": fields[9] if len(fields) > 9 else "",
            "remark": fields[10] if len(fields) > 10 else "",
        })

    return stations

