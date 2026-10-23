CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS flight_record (
    flight_id BIGSERIAL PRIMARY KEY,
    flight_no VARCHAR(20) NOT NULL, airline VARCHAR(100), aircraft_type VARCHAR(50), aircraft_registration VARCHAR(30),
    departure_icao VARCHAR(10) NOT NULL, departure_airport_name VARCHAR(150), departure_terminal VARCHAR(30), boarding_method VARCHAR(30), departure_time TIMESTAMP NOT NULL,
    arrival_icao VARCHAR(10) NOT NULL, arrival_airport_name VARCHAR(150), arrival_terminal VARCHAR(30), deboarding_method VARCHAR(30), arrival_time TIMESTAMP,
    stopovers JSONB NOT NULL DEFAULT '[]'::jsonb, seat_no VARCHAR(30), cabin_class VARCHAR(30), distance_km NUMERIC(10,2), note TEXT,
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
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_train_time CHECK (arrival_time IS NULL OR arrival_time >= departure_time)
);
ALTER TABLE train_record DROP COLUMN IF EXISTS route_source, DROP COLUMN IF EXISTS route_captured_at;

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
    location geometry(Point,4326), region_id VARCHAR(120),
    location_type VARCHAR(100), note TEXT,
    media_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_footprint_type CHECK (visit_type IN ('travel','transit')),
    CONSTRAINT chk_footprint_media_urls CHECK (jsonb_typeof(media_urls) = 'array' AND jsonb_array_length(media_urls) <= 9)
);
CREATE INDEX IF NOT EXISTS idx_footprint_location ON footprint USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_footprint_region_id ON footprint (region_id) WHERE region_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS trajectory (
    trajectory_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    activity_type VARCHAR(24) NOT NULL DEFAULT 'walk',
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    distance_m DOUBLE PRECISION,
    duration_s INTEGER,
    elevation_gain_m DOUBLE PRECISION,
    elevation_loss_m DOUBLE PRECISION,
    route_geometry geometry(MultiLineStringZ,4326),
    point_count INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trajectory_time CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT chk_trajectory_distance CHECK (distance_m IS NULL OR distance_m >= 0),
    CONSTRAINT chk_trajectory_duration CHECK (duration_s IS NULL OR duration_s >= 0),
    CONSTRAINT chk_trajectory_point_count CHECK (point_count >= 0)
);
ALTER TABLE trajectory ALTER COLUMN route_geometry DROP NOT NULL;
ALTER TABLE trajectory ALTER COLUMN route_geometry TYPE geometry(MultiLineStringZ,4326)
    USING CASE WHEN route_geometry IS NULL THEN NULL ELSE ST_Multi(route_geometry) END;
ALTER TABLE trajectory DROP CONSTRAINT IF EXISTS chk_trajectory_point_count;
ALTER TABLE trajectory ADD CONSTRAINT chk_trajectory_point_count CHECK (point_count >= 0);
CREATE INDEX IF NOT EXISTS idx_trajectory_started_at ON trajectory (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_trajectory_route_geometry ON trajectory USING GIST (route_geometry);

CREATE TABLE IF NOT EXISTS trajectory_point (
    trajectory_id BIGINT NOT NULL REFERENCES trajectory(trajectory_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    segment_no INTEGER NOT NULL DEFAULT 1,
    recorded_at TIMESTAMPTZ NOT NULL,
    location geometry(PointZ,4326) NOT NULL,
    accuracy_m REAL,
    speed_mps REAL,
    PRIMARY KEY (trajectory_id, sequence),
    CONSTRAINT chk_trajectory_point_sequence CHECK (sequence > 0),
    CONSTRAINT chk_trajectory_point_segment CHECK (segment_no > 0),
    CONSTRAINT chk_trajectory_point_accuracy CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
    CONSTRAINT chk_trajectory_point_speed CHECK (speed_mps IS NULL OR speed_mps >= 0)
);
ALTER TABLE trajectory_point ADD COLUMN IF NOT EXISTS segment_no INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trajectory_point DROP CONSTRAINT IF EXISTS chk_trajectory_point_segment;
ALTER TABLE trajectory_point ADD CONSTRAINT chk_trajectory_point_segment CHECK (segment_no > 0);
CREATE INDEX IF NOT EXISTS idx_trajectory_point_recorded_at ON trajectory_point (trajectory_id, recorded_at);

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END';

DROP TRIGGER IF EXISTS trg_flight_updated_at ON flight_record;
CREATE TRIGGER trg_flight_updated_at BEFORE UPDATE ON flight_record FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_train_updated_at ON train_record;
CREATE TRIGGER trg_train_updated_at BEFORE UPDATE ON train_record FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_footprint_updated_at ON footprint;
CREATE TRIGGER trg_footprint_updated_at BEFORE UPDATE ON footprint FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_trajectory_updated_at ON trajectory;
CREATE TRIGGER trg_trajectory_updated_at BEFORE UPDATE ON trajectory FOR EACH ROW EXECUTE FUNCTION set_updated_at();
