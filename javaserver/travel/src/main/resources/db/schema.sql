CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS flight_record (
    flight_id BIGSERIAL PRIMARY KEY,
    flight_no VARCHAR(20) NOT NULL, airline VARCHAR(100), aircraft_type VARCHAR(50), aircraft_registration VARCHAR(30),
    departure_icao VARCHAR(10) NOT NULL, departure_airport_name VARCHAR(150), departure_terminal VARCHAR(30), boarding_method VARCHAR(30), departure_time TIMESTAMP NOT NULL,
    arrival_icao VARCHAR(10) NOT NULL, arrival_airport_name VARCHAR(150), arrival_terminal VARCHAR(30), deboarding_method VARCHAR(30), arrival_time TIMESTAMP,
    stopovers JSONB NOT NULL DEFAULT '[]'::jsonb, seat_no VARCHAR(30), distance_km NUMERIC(10,2), note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_flight_time CHECK (arrival_time IS NULL OR arrival_time >= departure_time)
);
CREATE INDEX IF NOT EXISTS idx_flight_route ON flight_record (departure_icao, arrival_icao);

CREATE TABLE IF NOT EXISTS train_record (
    train_id BIGSERIAL PRIMARY KEY, train_no VARCHAR(30) NOT NULL,
    train_type VARCHAR(30), train_model VARCHAR(50),
    start_station_name VARCHAR(100) NOT NULL, departure_time TIMESTAMP NOT NULL,
    end_station_name VARCHAR(100) NOT NULL, arrival_time TIMESTAMP,
    origin_station_name VARCHAR(100), terminal_station_name VARCHAR(100),
    carriage_no VARCHAR(30), seat_no VARCHAR(30), seat_type VARCHAR(50), mileage_km NUMERIC(10,2),
    route_geometry geometry(MultiLineString,4326),
    route_source VARCHAR(100), route_captured_at TIMESTAMP, note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_train_time CHECK (arrival_time IS NULL OR arrival_time >= departure_time)
);

CREATE INDEX IF NOT EXISTS idx_train_number ON train_record (train_no);
CREATE INDEX IF NOT EXISTS idx_train_route_geometry ON train_record USING GIST (route_geometry);

CREATE TABLE IF NOT EXISTS train_waypoint (
    waypoint_id BIGSERIAL PRIMARY KEY,
    train_id BIGINT NOT NULL REFERENCES train_record(train_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    station_name VARCHAR(100) NOT NULL,
    arrival_time TIMESTAMP,
    departure_time TIMESTAMP,
    location geometry(Point,4326),
    CONSTRAINT uq_train_waypoint_sequence UNIQUE (train_id, sequence),
    CONSTRAINT chk_train_waypoint_sequence CHECK (sequence > 0)
);
CREATE INDEX IF NOT EXISTS idx_train_waypoint_train ON train_waypoint (train_id, sequence);
CREATE INDEX IF NOT EXISTS idx_train_waypoint_location ON train_waypoint USING GIST (location);

CREATE TABLE IF NOT EXISTS footprint (
    footprint_id BIGSERIAL PRIMARY KEY,
    place_name VARCHAR(200) NOT NULL, visit_type VARCHAR(20) NOT NULL DEFAULT 'travel', visit_date DATE,
    location geometry(Point,4326), poi_reference VARCHAR(100), note TEXT,
    media_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_footprint_type CHECK (visit_type IN ('travel','transit')),
    CONSTRAINT chk_footprint_media_ids CHECK (jsonb_typeof(media_ids) = 'array' AND jsonb_array_length(media_ids) <= 9)
);
ALTER TABLE footprint ADD COLUMN IF NOT EXISTS media_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
UPDATE footprint
SET media_ids = jsonb_build_array(to_jsonb(footprint)->>'media_id')
WHERE to_jsonb(footprint) ? 'media_id'
  AND to_jsonb(footprint)->>'media_id' IS NOT NULL
  AND media_ids = '[]'::jsonb;
ALTER TABLE footprint DROP COLUMN IF EXISTS media_id;
CREATE INDEX IF NOT EXISTS idx_footprint_location ON footprint USING GIST (location);

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END';

DROP TRIGGER IF EXISTS trg_flight_updated_at ON flight_record;
CREATE TRIGGER trg_flight_updated_at BEFORE UPDATE ON flight_record FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_train_updated_at ON train_record;
CREATE TRIGGER trg_train_updated_at BEFORE UPDATE ON train_record FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_footprint_updated_at ON footprint;
CREATE TRIGGER trg_footprint_updated_at BEFORE UPDATE ON footprint FOR EACH ROW EXECUTE FUNCTION set_updated_at();
