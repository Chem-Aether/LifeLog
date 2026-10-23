CREATE TABLE IF NOT EXISTS diary_entry (
    diary_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    entry_date DATE NOT NULL,
    mood VARCHAR(32) NOT NULL DEFAULT '',
    weather_code INTEGER,
    location VARCHAR(160) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE diary_entry DROP COLUMN IF EXISTS user_id;
ALTER TABLE diary_entry ADD COLUMN IF NOT EXISTS mood VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE diary_entry ADD COLUMN IF NOT EXISTS weather_code INTEGER;
ALTER TABLE diary_entry ADD COLUMN IF NOT EXISTS location VARCHAR(160) NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_diary_entry_date ON diary_entry (entry_date DESC, diary_id DESC);

CREATE TABLE IF NOT EXISTS diary_tag (
    tag_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS diary_entry_tag (
    diary_id BIGINT NOT NULL REFERENCES diary_entry(diary_id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES diary_tag(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (diary_id, tag_id)
);

CREATE TABLE IF NOT EXISTS diary_entry_media (
    diary_id BIGINT NOT NULL REFERENCES diary_entry(diary_id) ON DELETE CASCADE,
    media_url VARCHAR(2048) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (diary_id, media_url)
);

