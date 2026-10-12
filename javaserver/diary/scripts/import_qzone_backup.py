import argparse
import html
import json
import os
import re
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path


ACCOUNT_ID = "3254806570"
SOURCE = "qq-space"
EMOJI_TAG = re.compile(r"\[em\]e\d+\[/em\]", re.IGNORECASE)


def q(value):
    return "'" + str(value).replace("'", "''") + "'"


def media_paths(record, backup_root):
    result = []
    missing = 0
    for image in record.get("custom_images") or []:
        relative = image.get("custom_filepath")
        if not relative:
            missing += 1
            continue
        path = (backup_root / Path(relative.replace("\\", "/"))).resolve()
        if not path.is_relative_to(backup_root) or not path.is_file():
            missing += 1
            continue
        result.append((relative.replace("\\", "/"), path))
    return result, missing


def entry_date(record):
    value = str(record.get("custom_create_time") or "")
    for pattern in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value, pattern).date().isoformat()
        except ValueError:
            pass
    stamp = record.get("created_time")
    if stamp:
        return datetime.fromtimestamp(int(stamp), timezone.utc).astimezone().date().isoformat()
    raise ValueError(f"动态 {record.get('tid', '(无 tid)')} 缺少可识别日期")


def clean_content(record):
    content = html.unescape(str(record.get("custom_content") or record.get("content") or ""))
    return EMOJI_TAG.sub("", content).strip()


def title_for(content, date):
    excerpt = next((line.strip() for line in content.splitlines() if line.strip()), "")
    title = f"QQ空间 · {excerpt}" if excerpt else f"QQ空间动态 · {date}"
    return title[:200]


def load_records(backup_root):
    candidates = [
        backup_root / "Common/json" / f"助手备份数据_{ACCOUNT_ID}.json",
        backup_root / "Common/json" / "助手备份数据.json",
    ]
    data_file = next((path for path in candidates if path.is_file()), None)
    if not data_file:
        raise FileNotFoundError("找不到 Common/json/助手备份数据*.json")
    root = json.loads(data_file.read_text(encoding="utf-8-sig"))
    modules = root.get("Backedup", {}).get(ACCOUNT_ID, [])
    messages = next((item.get("data", []) for item in modules if item.get("module") == "Messages"), None)
    if messages is None:
        raise ValueError("备份中没有 Messages 动态模块")
    records = []
    for record in messages:
        key = str(record.get("tid") or "").strip()
        if not key:
            raise ValueError("存在没有 tid 的动态，拒绝生成不可幂等的导入记录")
        date = entry_date(record)
        content = clean_content(record)
        images, missing = media_paths(record, backup_root)
        videos = len(record.get("custom_videos") or [])
        truncated = len(content) > 20_000
        if truncated:
            marker = "\n[正文超出日记长度限制，已截断]"
            content = content[:20_000 - len(marker)] + marker
        records.append({
            "source_key": f"{SOURCE}:{ACCOUNT_ID}:{key}",
            "title": title_for(content, date),
            "content": content,
            "entry_date": date,
            "images": images,
            "missing_images": missing,
            "videos": videos,
            "truncated": truncated,
        })
    return records


def run_psql(database_url, sql, capture=False):
    psql = shutil.which("psql")
    if not psql:
        raise RuntimeError("找不到 psql，请先安装 PostgreSQL 客户端")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as file:
        file.write("SET client_encoding = 'UTF8';\n")
        file.write(sql)
        script_path = file.name
    try:
        result = subprocess.run(
            [psql, "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--file", script_path, database_url],
            check=True, text=True, encoding="utf-8", capture_output=capture,
        )
        return result.stdout if capture else ""
    finally:
        Path(script_path).unlink(missing_ok=True)


def sql_rows(records, media_ids):
    lines = [
        "BEGIN;",
        "CREATE TABLE IF NOT EXISTS diary_import_source (source_key TEXT PRIMARY KEY, diary_id BIGINT NOT NULL UNIQUE REFERENCES diary_entry(diary_id) ON DELETE CASCADE);",
        "INSERT INTO diary_tag(name) VALUES ('QQ空间') ON CONFLICT (name) DO NOTHING;",
    ]
    for record in records:
        key = q(record["source_key"])
        lines.append(
            "WITH fresh AS ("
            "INSERT INTO diary_entry(title,content,entry_date) "
            f"SELECT {q(record['title'])},{q(record['content'])},{q(record['entry_date'])}::date "
            f"WHERE NOT EXISTS (SELECT 1 FROM diary_import_source WHERE source_key={key}) "
            "RETURNING diary_id) "
            f"INSERT INTO diary_import_source(source_key,diary_id) SELECT {key},diary_id FROM fresh "
            "ON CONFLICT (source_key) DO NOTHING;"
        )
        lines.append(
            "INSERT INTO diary_entry_tag(diary_id,tag_id) "
            "SELECT source.diary_id,tag.tag_id FROM diary_import_source source "
            "JOIN diary_tag tag ON tag.name='QQ空间' "
            f"WHERE source.source_key={key} ON CONFLICT DO NOTHING;"
        )
        ids = [media_ids[path] for path, _ in record["images"] if path in media_ids]
        if ids:
            array = "ARRAY[" + ",".join(q(media_id) for media_id in ids) + "]::text[]"
            lines.append(
                "INSERT INTO diary_entry_media(diary_id,media_id,sort_order) "
                f"SELECT source.diary_id,media.media_id,media.ordinality::int-1 "
                f"FROM diary_import_source source CROSS JOIN LATERAL unnest({array}) WITH ORDINALITY AS media(media_id,ordinality) "
                f"WHERE source.source_key={key} ON CONFLICT (diary_id,media_id) DO NOTHING;"
            )
    lines.append("COMMIT;")
    return "\n".join(lines)


def upload_image(session, url, category, headers, path):
    import io
    import requests
    from PIL import Image, ImageOps

    with path.open("rb") as source:
        payload = source.read()
    try:
        with Image.open(io.BytesIO(payload)) as image:
            image_format = image.format
            if image_format in {"JPEG", "PNG", "WEBP", "GIF"}:
                upload_name = path.name
                upload_data = payload
                content_type = {
                    "JPEG": "image/jpeg", "PNG": "image/png",
                    "WEBP": "image/webp", "GIF": "image/gif",
                }[image_format]
            else:
                image = ImageOps.exif_transpose(image).convert("RGB")
                converted = io.BytesIO()
                image.save(converted, format="JPEG", quality=92)
                upload_name = f"{path.stem}.jpg"
                upload_data = converted.getvalue()
                content_type = "image/jpeg"
    except (OSError, ValueError):
        return None

    response = session.post(
        url, files={"file": (upload_name, upload_data, content_type)},
        data={"category": category}, headers=headers, timeout=(15, 180),
    )
    if not response.ok:
        raise RuntimeError(
            f"媒体上传失败: {path} ({response.status_code}): {response.text[:1000]}"
        )
    return response.json()["media_id"]


def main():
    parser = argparse.ArgumentParser(description="One-time import of a QQ Space backup into LifeLog diary")
    parser.add_argument("backup_dir", type=Path)
    parser.add_argument("--database-url", default=os.getenv("DIARY_DB_URL"))
    parser.add_argument("--media-url", default=os.getenv("MEDIA_BASE_URL", "http://127.0.0.1:8780"))
    parser.add_argument("--state-file", type=Path, default=Path.home() / ".lifelog" / "qzone-media-state.json")
    parser.add_argument("--execute", action="store_true", help="Upload media and insert diary entries; omitted means dry-run")
    args = parser.parse_args()
    backup_root = args.backup_dir.resolve()
    records = load_records(backup_root)
    unique_images = {path: file for record in records for path, file in record["images"]}
    missing = sum(record["missing_images"] for record in records)
    videos = sum(record["videos"] for record in records)
    contents_over_limit = sum(record["truncated"] for record in records)
    dates = [record["entry_date"] for record in records]
    print(f"动态: {len(records)} 篇，日期: {min(dates)} 至 {max(dates)}")
    print(f"有正文: {sum(bool(record['content']) for record in records)} 篇；唯一可用本地图片: {len(unique_images)} 张")
    print(f"缺失/非本地图片: {missing} 张；视频: {videos} 个（本次不导入）；正文截断: {contents_over_limit} 篇")
    print(f"单篇最多图片: {max((len(record['images']) for record in records), default=0)} 张；评论与点赞不导入")
    if not args.execute:
        print("当前为预检模式，没有上传媒体或写入数据库。使用 --execute 才会执行导入。")
        return
    if not args.database_url:
        raise SystemExit("执行导入需要 --database-url 或 DIARY_DB_URL")

    import requests

    run_psql(args.database_url, "CREATE TABLE IF NOT EXISTS diary_import_source (source_key TEXT PRIMARY KEY, diary_id BIGINT NOT NULL UNIQUE REFERENCES diary_entry(diary_id) ON DELETE CASCADE);", capture=True)
    existing = set(run_psql(
        args.database_url,
        f"COPY (SELECT source_key FROM diary_import_source WHERE source_key LIKE {q(SOURCE + ':' + ACCOUNT_ID + ':%')}) TO STDOUT;",
        capture=True,
    ).splitlines())
    pending = [record for record in records if record["source_key"] not in existing]
    pending_paths = {path: file for record in pending for path, file in record["images"]}
    args.state_file.parent.mkdir(parents=True, exist_ok=True)
    state = json.loads(args.state_file.read_text(encoding="utf-8")) if args.state_file.exists() else {}
    session = requests.Session()
    media_key = os.getenv("MEDIA_API_KEY")
    headers = {"X-Media-Key": media_key} if media_key else {}
    media_ids = {}
    corrupt_images = []
    for index, (relative, path) in enumerate(pending_paths.items(), start=1):
        saved = state.get(relative)
        if saved:
            try:
                check = session.head(f"{args.media_url.rstrip('/')}/media/{saved}", timeout=15)
                if check.ok:
                    media_ids[relative] = saved
                    continue
            except requests.RequestException:
                pass
        media_id = upload_image(
            session, f"{args.media_url.rstrip('/')}/api/media", "diary", headers, path,
        )
        if media_id is None:
            corrupt_images.append(relative)
            continue
        state[relative] = media_id
        media_ids[relative] = media_id
        if index % 20 == 0:
            args.state_file.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
            print(f"媒体上传进度: {index}/{len(pending_paths)}")
    args.state_file.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
    sql = sql_rows(pending, media_ids)
    run_psql(args.database_url, sql)
    print(f"导入完成: 新增 {len(pending)} 篇；已存在并跳过 {len(existing)} 篇。")
    print(f"备份中损坏图片已跳过: {len(corrupt_images)} 张。")
    print(f"媒体续传映射保存在: {args.state_file}")


if __name__ == "__main__":
    main()
