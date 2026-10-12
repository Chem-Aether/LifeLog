package com.lifelog.travel.footprint;

import com.lifelog.travel.footprint.FootSpot;
import com.lifelog.travel.footprint.FootSpotMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FootprintGeoPackageService {
    private static final int GPKG_APPLICATION_ID = 0x47504B47;
    private static final String WGS84_WKT = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4326\"]]";
    private final FootSpotMapper footprints;

    public FootprintGeoPackageService(FootSpotMapper footprints) {
        this.footprints = footprints;
    }

    public byte[] exportAll() throws IOException {
        return export(null);
    }

    public byte[] exportOne(Long id) throws IOException {
        return export(id);
    }

    private byte[] export(Long id) throws IOException {
        Path temp = Files.createTempFile("lifelog-footprints-", ".gpkg");
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp)) {
                connection.setAutoCommit(false);
                createSchema(connection);
                List<FootSpot> rows = footprints.selectRecords(null).stream()
                        .filter(row -> id == null || id.equals(row.getFootprintId())).toList();
                insertRows(connection, rows);
                connection.commit();
            } catch (SQLException exception) {
                throw new IOException("创建足迹 GeoPackage 失败", exception);
            }
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id=" + GPKG_APPLICATION_ID);
            statement.execute("PRAGMA user_version=10300");
            statement.execute("CREATE TABLE gpkg_spatial_ref_sys (srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY, organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL, definition TEXT NOT NULL, description TEXT)");
            statement.execute("CREATE TABLE gpkg_contents (table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL, identifier TEXT UNIQUE, description TEXT DEFAULT '', last_change DATETIME NOT NULL, min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER, FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))");
            statement.execute("CREATE TABLE gpkg_geometry_columns (table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL, PRIMARY KEY (table_name, column_name), FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name), FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))");
            statement.execute("CREATE TABLE footprints (fid INTEGER PRIMARY KEY AUTOINCREMENT, footprint_id INTEGER NOT NULL UNIQUE, place_name TEXT NOT NULL, visit_type TEXT NOT NULL, visit_date TEXT, poi_reference TEXT, note TEXT, media_ids TEXT NOT NULL, longitude DOUBLE, latitude DOUBLE, geom POINT)");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('Undefined Cartesian', -1, 'NONE', -1, 'undefined', 'undefined Cartesian coordinate reference system')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('Undefined Geographic', 0, 'NONE', 0, 'undefined', 'undefined geographic coordinate reference system')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('WGS 84 geodetic', 4326, 'EPSG', 4326, '" + WGS84_WKT.replace("'", "''") + "', 'longitude/latitude coordinates on the WGS 84 spheroid')");
            String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
            try (PreparedStatement contents = connection.prepareStatement("INSERT INTO gpkg_contents (table_name,data_type,identifier,description,last_change,srs_id) VALUES ('footprints','features','旅行足迹','地点点位及旅行记录',?,4326)")) {
                contents.setString(1, timestamp);
                contents.executeUpdate();
            }
            statement.execute("INSERT INTO gpkg_geometry_columns VALUES ('footprints','geom','POINT',4326,0,0)");
        }
    }

    private void insertRows(Connection connection, List<FootSpot> rows) throws SQLException {
        String sql = "INSERT INTO footprints (footprint_id,place_name,visit_type,visit_date,poi_reference,note,media_ids,longitude,latitude,geom) VALUES (?,?,?,?,?,?,?,?,?,?)";
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (FootSpot row : rows) {
                statement.setLong(1, row.getFootprintId());
                statement.setString(2, row.getPlaceName());
                statement.setString(3, row.getVisitType());
                statement.setString(4, row.getVisitDate() == null ? null : row.getVisitDate().toString());
                statement.setString(5, row.getPoiReference());
                statement.setString(6, row.getNote());
                statement.setString(7, row.getMediaIdsJson());
                if (row.getLongitude() == null || row.getLatitude() == null) {
                    statement.setNull(8, Types.DOUBLE);
                    statement.setNull(9, Types.DOUBLE);
                    statement.setNull(10, Types.BLOB);
                } else {
                    double longitude = row.getLongitude().doubleValue();
                    double latitude = row.getLatitude().doubleValue();
                    if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                            || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                        throw new SQLException("足迹坐标超出经纬度有效范围，记录 ID: " + row.getFootprintId());
                    }
                    statement.setDouble(8, longitude);
                    statement.setDouble(9, latitude);
                    statement.setBytes(10, pointBlob(longitude, latitude));
                    minX = Math.min(minX, longitude); minY = Math.min(minY, latitude);
                    maxX = Math.max(maxX, longitude); maxY = Math.max(maxY, latitude);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
        if (Double.isFinite(minX)) {
            try (PreparedStatement bounds = connection.prepareStatement("UPDATE gpkg_contents SET min_x=?,min_y=?,max_x=?,max_y=? WHERE table_name='footprints'")) {
                bounds.setDouble(1, minX); bounds.setDouble(2, minY); bounds.setDouble(3, maxX); bounds.setDouble(4, maxY);
                bounds.executeUpdate();
            }
        }
    }

    private byte[] pointBlob(double longitude, double latitude) {
        ByteBuffer buffer = ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(4326);
        buffer.put((byte) 1).putInt(1).putDouble(longitude).putDouble(latitude);
        return buffer.array();
    }
}
