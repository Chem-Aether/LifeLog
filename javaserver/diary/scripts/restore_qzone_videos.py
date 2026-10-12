import argparse
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


ACCOUNT_ID = "3254806570"


def sql_quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def load_videos(root):
    data_path = root / "Common/json" / f"助手备份数据_{ACCOUNT_ID}.json"
    data = json.loads(data_path.read_text(encoding="utf-8-sig"))
    messages = next(module["data"] for module in data["Backedup"][ACCOUNT_ID] if module["module"] == "Messages")
    records = []
    for record in messages:
        paths = []
        for video in record.get("custom_videos") or []:
            relative = str(video.get("custom_filepath") or "").replace("\\", "/")
            path = (root / relative).resolve()
            if relative and path.is_relative_to(root) and path.is_file():
                paths.append((relative, path))
        if paths:
            records.append((str(record["tid"]), paths))
    return records


def psql(database_url, sql):
    executable = shutil.which("psql")
    if not executable:
        raise RuntimeError("找不到 psql")
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as handle:
        handle.write("SET client_encoding='UTF8';\n")
        handle.write(sql)
        script = Path(handle.name)
    try:
        subprocess.run([executable, "-X", "-q", "--set", "ON_ERROR_STOP=1", "--file", str(script), database_url], check=True)
    finally:
        script.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("backup_dir", type=Path)
    parser.add_argument("--database-url", default=os.getenv("DIARY_DB_URL"))
    parser.add_argument("--media-url", default=os.getenv("MEDIA_BASE_URL", "http://127.0.0.1:8780"))
    parser.add_argument("--state-file", type=Path, required=True)
    args = parser.parse_args()
    if not args.database_url:
        raise SystemExit("需要 DIARY_DB_URL")

    import requests

    root = args.backup_dir.resolve()
    records = load_videos(root)
    unique = {relative: path for _, paths in records for relative, path in paths}
    state = json.loads(args.state_file.read_text(encoding="utf-8")) if args.state_file.exists() else {}
    args.state_file.parent.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    key = os.getenv("MEDIA_API_KEY")
    headers = {"X-Media-Key": key} if key else {}
    for index, (relative, path) in enumerate(unique.items(), start=1):
        media_id = state.get(relative)
        if media_id:
            try:
                if session.head(f"{args.media_url.rstrip('/')}/media/{media_id}", timeout=15).ok:
                    continue
            except requests.RequestException:
                pass
        with path.open("rb") as file:
            response = session.post(
                f"{args.media_url.rstrip('/')}/api/media",
                files={"file": (path.name, file, "video/mp4")},
                data={"category": "diary", "media_type": "video"},
                headers=headers, timeout=(30, 600),
            )
        if not response.ok:
            raise RuntimeError(f"视频上传失败 {relative}: HTTP {response.status_code}: {response.text[:500]}")
        state[relative] = response.json()["media_id"]
        if index % 5 == 0:
            args.state_file.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
            print(f"视频上传进度: {index}/{len(unique)}", flush=True)
    args.state_file.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")

    statements = ["BEGIN;"]
    for tid, paths in records:
        source_key = f"qq-space:{ACCOUNT_ID}:{tid}"
        media_ids = [state[relative] for relative, _ in paths if relative in state]
        if not media_ids:
            continue
        ids = "ARRAY[" + ",".join(sql_quote(media_id) for media_id in media_ids) + "]::text[]"
        statements.append(
            "INSERT INTO diary_entry_media(diary_id,media_id,sort_order) "
            "SELECT source.diary_id,video.media_id,COALESCE((SELECT max(sort_order)+1 FROM diary_entry_media WHERE diary_id=source.diary_id),0)+video.ordinality::int-1 "
            f"FROM diary_import_source source CROSS JOIN LATERAL unnest({ids}) WITH ORDINALITY AS video(media_id,ordinality) "
            f"WHERE source.source_key={sql_quote(source_key)} ON CONFLICT(diary_id,media_id) DO NOTHING;"
        )
    statements.append("COMMIT;")
    psql(args.database_url, "\n".join(statements))
    print(f"视频恢复完成: {len(unique)} 个唯一视频，覆盖 {len(records)} 篇动态；不会导入评论。")


if __name__ == "__main__":
    main()
