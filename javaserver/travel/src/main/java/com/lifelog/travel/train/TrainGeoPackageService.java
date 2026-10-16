package com.lifelog.travel.train;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 负责铁路行程 GeoPackage 的创建、导出、导入及几何编码。 */
@Service
public class TrainGeoPackageService {
    private static final int GPKG_APPLICATION_ID = 0x47504B47;
    private static final String WGS84_WKT = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4326\"]]";
    private final TrainRecordMapper trains;
    private final TrainWaypointMapper waypoints;
    private final TrainTicketService trainService;
    private final ObjectMapper objectMapper;

    /** 创建铁路 GeoPackage 服务。 */
    public TrainGeoPackageService(TrainRecordMapper trains, TrainWaypointMapper waypoints,
                                  TrainTicketService trainService, ObjectMapper objectMapper) {
        this.trains = trains;
        this.waypoints = waypoints;
        this.trainService = trainService;
        this.objectMapper = objectMapper;
    }

    /** 导出全部铁路行程及线路数据。 */
    public byte[] exportAll() throws IOException { return export(null, false); }
    /** 导出指定铁路行程及线路数据。 */
    public byte[] exportOne(Long id) throws IOException { return export(id, false); }
    /** 创建铁路行程导入模板。 */
    public byte[] exportTemplate() throws IOException { return export(null, true); }

    /** 生成临时 GeoPackage，并写入行程、线路和途经站。 */
    private byte[] export(Long id, boolean template) throws IOException {
        Path temp = Files.createTempFile("lifelog-trains-", ".gpkg");
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp)) {
                connection.setAutoCommit(false);
                createSchema(connection);
                List<TrainRecord> records = template ? List.of() : trains.selectRecords(null).stream()
                        .filter(row -> id == null || id.equals(row.getTrainId())).toList();
                insertRecords(connection, records);
                Set<Long> ids = new HashSet<>();
                records.forEach(row -> ids.add(row.getTrainId()));
                if (!template) {
                    insertRoutes(connection, records);
                    insertWaypoints(connection, ids);
                }
                connection.commit();
            } catch (SQLException exception) {
                throw new IOException("创建铁路 GeoPackage 失败", exception);
            }
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 创建 GeoPackage 元数据表及铁路属性、线路和途经站图层。 */
    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id=" + GPKG_APPLICATION_ID);
            statement.execute("PRAGMA user_version=10300");
            statement.execute("CREATE TABLE gpkg_spatial_ref_sys (srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY, organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL, definition TEXT NOT NULL, description TEXT)");
            statement.execute("CREATE TABLE gpkg_contents (table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL, identifier TEXT UNIQUE, description TEXT DEFAULT '', last_change DATETIME NOT NULL, min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE, srs_id INTEGER, FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))");
            statement.execute("CREATE TABLE gpkg_geometry_columns (table_name TEXT NOT NULL, column_name TEXT NOT NULL, geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL, z TINYINT NOT NULL, m TINYINT NOT NULL, PRIMARY KEY (table_name, column_name), FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name), FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))");
            statement.execute("CREATE TABLE train_records (train_id INTEGER PRIMARY KEY, train_no TEXT NOT NULL, train_type TEXT, train_model TEXT, start_station_name TEXT NOT NULL, departure_time TEXT NOT NULL, end_station_name TEXT NOT NULL, arrival_time TEXT, origin_station_name TEXT, terminal_station_name TEXT, carriage_no TEXT, seat_no TEXT, seat_type TEXT, mileage_km REAL, route_source TEXT, route_captured_at TEXT, note TEXT)");
            statement.execute("CREATE TABLE train_routes (fid INTEGER PRIMARY KEY AUTOINCREMENT, train_id INTEGER NOT NULL UNIQUE, geom GEOMETRY NOT NULL, FOREIGN KEY (train_id) REFERENCES train_records(train_id))");
            statement.execute("CREATE TABLE train_waypoints (fid INTEGER PRIMARY KEY AUTOINCREMENT, train_id INTEGER NOT NULL, sequence INTEGER NOT NULL, station_name TEXT NOT NULL, arrival_time TEXT, departure_time TEXT, longitude REAL, latitude REAL, geom POINT, FOREIGN KEY (train_id) REFERENCES train_records(train_id))");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('Undefined Cartesian', -1, 'NONE', -1, 'undefined', 'undefined Cartesian coordinate reference system')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('Undefined Geographic', 0, 'NONE', 0, 'undefined', 'undefined geographic coordinate reference system')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES ('WGS 84 geodetic', 4326, 'EPSG', 4326, '" + WGS84_WKT.replace("'", "''") + "', 'longitude/latitude coordinates on the WGS 84 spheroid')");
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        try (PreparedStatement contents = connection.prepareStatement("INSERT INTO gpkg_contents (table_name,data_type,identifier,description,last_change,srs_id) VALUES (?,?,?,?,?,?)")) {
            addContents(contents, "train_records", "attributes", "列车信息", "列车行程及非空间属性", timestamp, null);
            addContents(contents, "train_routes", "features", "铁路行程轨迹", "列车路线几何", timestamp, 4326);
            addContents(contents, "train_waypoints", "features", "列车途经站", "有顺序的途经站点及站点坐标", timestamp, 4326);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO gpkg_geometry_columns VALUES ('train_routes','geom','GEOMETRY',4326,0,0)");
            statement.execute("INSERT INTO gpkg_geometry_columns VALUES ('train_waypoints','geom','POINT',4326,0,0)");
        }
    }

    /** 写入一个 GeoPackage 图层的元数据记录。 */
    private void addContents(PreparedStatement statement, String table, String type, String name,
                             String description, String timestamp, Integer srsId) throws SQLException {
        statement.setString(1, table); statement.setString(2, type); statement.setString(3, name);
        statement.setString(4, description); statement.setString(5, timestamp);
        if (srsId == null) statement.setNull(6, Types.INTEGER); else statement.setInt(6, srsId);
        statement.executeUpdate();
    }

    /** 批量写入铁路行程属性记录。 */
    private void insertRecords(Connection connection, List<TrainRecord> records) throws SQLException {
        String sql = "INSERT INTO train_records (train_id,train_no,train_type,train_model,start_station_name,departure_time,end_station_name,arrival_time,origin_station_name,terminal_station_name,carriage_no,seat_no,seat_type,mileage_km,route_source,route_captured_at,note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TrainRecord row : records) {
                if (row.getTrainId() == null) throw new SQLException("列车记录缺少 train_id，无法导出 GeoPackage");
                statement.setLong(1, row.getTrainId()); statement.setString(2, row.getTrainNo());
                statement.setString(3, row.getTrainType()); statement.setString(4, row.getTrainModel());
                statement.setString(5, row.getStartStationName());
                statement.setString(6, string(row.getDepartureTime()));
                statement.setString(7, row.getEndStationName()); statement.setString(8, string(row.getArrivalTime()));
                statement.setString(9, row.getOriginStationName()); statement.setString(10, row.getTerminalStationName());
                statement.setString(11, row.getCarriageNo()); statement.setString(12, row.getSeatNo());
                statement.setString(13, row.getSeatType()); statement.setObject(14, row.getMileageKm());
                statement.setString(15, row.getRouteSource());
                statement.setString(16, string(row.getRouteCapturedAt())); statement.setString(17, row.getNote()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 将行程 GeoJSON 线路写入铁路轨迹图层。 */
    private void insertRoutes(Connection connection, List<TrainRecord> records) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO train_routes (train_id,geom) VALUES (?,?)")) {
            for (TrainRecord row : records) {
                if (row.getRouteGeoJson() == null || row.getRouteGeoJson().isBlank()) continue;
                statement.setLong(1, row.getTrainId());
                statement.setBytes(2, gpkgGeometry(geometry(row.getRouteGeoJson())));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 将指定行程的途经站及其点几何写入 GeoPackage。 */
    private void insertWaypoints(Connection connection, Set<Long> trainIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO train_waypoints (train_id,sequence,station_name,arrival_time,departure_time,longitude,latitude,geom) VALUES (?,?,?,?,?,?,?,?)")) {
            for (TrainWaypoint point : waypoints.selectAllRecords()) {
                if (!trainIds.contains(point.getTrainId())) continue;
                statement.setLong(1, point.getTrainId()); statement.setInt(2, point.getSequence());
                statement.setString(3, point.getStationName());
                statement.setString(4, string(point.getArrivalTime())); statement.setString(5, string(point.getDepartureTime()));
                if (point.getLongitude() == null || point.getLatitude() == null) {
                    statement.setNull(6, Types.DOUBLE); statement.setNull(7, Types.DOUBLE); statement.setNull(8, Types.BLOB);
                } else {
                    statement.setBigDecimal(6, point.getLongitude()); statement.setBigDecimal(7, point.getLatitude());
                    statement.setBytes(8, pointBlob(point.getLongitude().doubleValue(), point.getLatitude().doubleValue()));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 校验并导入铁路 GeoPackage 文件。 */
    public Map<String, Object> importFile(MultipartFile file, boolean updateExisting) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空 GeoPackage 文件");
        Path temp = Files.createTempFile("lifelog-trains-import-", ".gpkg");
        try {
            file.transferTo(temp);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp)) {
                try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA application_id")) {
                    if (!result.next() || result.getInt(1) != GPKG_APPLICATION_ID) throw new IllegalArgumentException("文件不是有效的 GeoPackage");
                }
                requireTable(connection, "train_records");
                Map<Long, List<TrainWaypoint>> points = readWaypoints(connection);
                Map<Long, String> routeGeometries = readRoutes(connection);
                return importRecords(connection, points, routeGeometries, updateExisting);
            } catch (SQLException exception) {
                throw new IllegalArgumentException("无法读取铁路 GeoPackage：" + exception.getMessage());
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 确认 GeoPackage 中存在指定数据表。 */
    private void requireTable(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("GeoPackage 缺少列车属性表 train_records");
            }
        }
    }

    /** 读取途经站记录并按行程 ID 分组。 */
    private Map<Long, List<TrainWaypoint>> readWaypoints(Connection connection) throws SQLException {
        Map<Long, List<TrainWaypoint>> result = new HashMap<>();
        if (!hasTable(connection, "train_waypoints")) return result;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT train_id,sequence,station_name,arrival_time,departure_time,longitude,latitude,geom FROM train_waypoints ORDER BY train_id,sequence")) {
            while (rows.next()) {
                TrainWaypoint point = new TrainWaypoint();
                point.setTrainId(rows.getLong("train_id")); point.setSequence(rows.getInt("sequence"));
                point.setStationName(rows.getString("station_name"));
                point.setArrivalTime(dateTime(rows.getString("arrival_time"))); point.setDepartureTime(dateTime(rows.getString("departure_time")));
                point.setLongitude(rows.getBigDecimal("longitude")); point.setLatitude(rows.getBigDecimal("latitude"));
                byte[] geometry = rows.getBytes("geom");
                if (geometry != null) {
                    double[] coordinates = pointCoordinates(geometry);
                    point.setLongitude(java.math.BigDecimal.valueOf(coordinates[0]));
                    point.setLatitude(java.math.BigDecimal.valueOf(coordinates[1]));
                }
                if ((point.getLongitude() == null) != (point.getLatitude() == null)) throw new IllegalArgumentException("途经站经纬度必须同时填写或留空");
                result.computeIfAbsent(point.getTrainId(), ignored -> new ArrayList<>()).add(point);
            }
        }
        return result;
    }

    /** 读取线路几何并转换为 GeoJSON。 */
    private Map<Long, String> readRoutes(Connection connection) throws SQLException, IOException {
        Map<Long, String> result = new HashMap<>();
        if (!hasTable(connection, "train_routes")) return result;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT train_id,geom FROM train_routes")) {
            while (rows.next()) {
                byte[] geometry = rows.getBytes("geom");
                if (geometry != null) result.put(rows.getLong("train_id"), geometryGeoJson(geometry));
            }
        }
        return result;
    }

    /** 检查 SQLite 文件中是否存在指定表。 */
    private boolean hasTable(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    /** 逐行校验并导入行程属性、线路和途经站，汇总错误信息。 */
    private Map<String, Object> importRecords(Connection connection, Map<Long, List<TrainWaypoint>> points,
                                               Map<Long, String> routeGeometries, boolean updateExisting) throws SQLException {
        int imported = 0;
        List<String> errors = new ArrayList<>();
        Set<Long> importedSourceIds = new HashSet<>();
        String sql = "SELECT train_id,train_no,train_type,train_model,start_station_name,departure_time,end_station_name,arrival_time,origin_station_name,terminal_station_name,carriage_no,seat_no,seat_type,mileage_km,route_source,route_captured_at,note FROM train_records ORDER BY train_id";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            int rowNumber = 1;
            while (rows.next()) {
                rowNumber++;
                try {
                    long sourceId = rows.getLong("train_id");
                    TrainRecord record = new TrainRecord();
                    record.setTrainNo(rows.getString("train_no")); record.setTrainType(rows.getString("train_type"));
                    record.setTrainModel(rows.getString("train_model"));
                    record.setStartStationName(rows.getString("start_station_name")); record.setDepartureTime(dateTime(rows.getString("departure_time")));
                    record.setEndStationName(rows.getString("end_station_name"));
                    record.setArrivalTime(dateTime(rows.getString("arrival_time"))); record.setOriginStationName(rows.getString("origin_station_name"));
                    record.setTerminalStationName(rows.getString("terminal_station_name")); record.setCarriageNo(rows.getString("carriage_no"));
                    record.setSeatNo(rows.getString("seat_no")); record.setSeatType(rows.getString("seat_type"));
                    record.setMileageKm(rows.getBigDecimal("mileage_km"));
                    record.setRouteSource(rows.getString("route_source"));
                    record.setRouteCapturedAt(dateTime(rows.getString("route_captured_at"))); record.setNote(rows.getString("note"));
                    record.setRouteGeoJson(routeGeometries.get(sourceId)); record.setWaypoints(points.getOrDefault(sourceId, List.of()));
                    if (updateExisting) trainService.update(sourceId, record);
                    else trainService.save(record);
                    importedSourceIds.add(sourceId);
                    imported++;
                } catch (Exception exception) {
                    errors.add("列车属性表第 " + rowNumber + " 行：" + rootMessage(exception));
                }
            }
        }
        for (Long trainId : points.keySet()) if (!importedSourceIds.contains(trainId)) errors.add("途经站关联的列车ID不存在：" + trainId);
        for (Long trainId : routeGeometries.keySet()) if (!importedSourceIds.contains(trainId)) errors.add("轨迹关联的列车ID不存在：" + trainId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("trainsImported", imported); report.put("totalImported", imported); report.put("errors", errors);
        return report;
    }

    /** 将可空对象转换为字符串。 */
    private String string(Object value) { return value == null ? null : value.toString(); }
    /** 解析 ISO 本地日期时间文本。 */
    private LocalDateTime dateTime(String value) { return value == null || value.isBlank() ? null : LocalDateTime.parse(value); }
    /** 获取异常链最深层的错误信息。 */
    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "数据格式无效" : cause.getMessage();
    }

    /** 解析并校验 LineString 或 MultiLineString GeoJSON。 */
    private JsonNode geometry(String geoJson) throws IOException {
        JsonNode node = objectMapper.readTree(geoJson);
        if (node.isTextual()) node = objectMapper.readTree(node.textValue());
        if (!node.has("type") || !node.has("coordinates")) throw new IllegalArgumentException("轨迹必须是 GeoJSON LineString 或 MultiLineString");
        String type = node.get("type").asText();
        if (!type.equals("LineString") && !type.equals("MultiLineString")) throw new IllegalArgumentException("轨迹必须是 GeoJSON LineString 或 MultiLineString");
        return node;
    }

    /** 将 GeoJSON 几何转换为带 WGS84 标识头的 GeoPackage 几何字节。 */
    private byte[] gpkgGeometry(JsonNode geometry) throws IOException {
        byte[] wkb = writeWkb(geometry);
        ByteBuffer result = ByteBuffer.allocate(8 + wkb.length).order(ByteOrder.LITTLE_ENDIAN);
        result.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(4326).put(wkb);
        return result.array();
    }

    /** 将 GeoJSON 线路几何编码为 WKB。 */
    private byte[] writeWkb(JsonNode geometry) {
        String type = geometry.get("type").asText();
        if (type.equals("LineString")) return writeLine(geometry.get("coordinates"));
        JsonNode lines = geometry.get("coordinates");
        List<byte[]> children = new ArrayList<>();
        int size = 9;
        for (JsonNode line : lines) { byte[] child = writeLine(line); children.add(child); size += child.length; }
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1).putInt(5).putInt(children.size());
        children.forEach(buffer::put);
        return buffer.array();
    }

    /** 将坐标序列编码为 WKB LineString。 */
    private byte[] writeLine(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.size() < 2) throw new IllegalArgumentException("轨迹每段至少需要两个坐标点");
        ByteBuffer buffer = ByteBuffer.allocate(9 + coordinates.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1).putInt(2).putInt(coordinates.size());
        for (JsonNode coordinate : coordinates) buffer.putDouble(coordinate.get(0).asDouble()).putDouble(coordinate.get(1).asDouble());
        return buffer.array();
    }

    /** 将 WGS84 点编码为 GeoPackage 点几何。 */
    private byte[] pointBlob(double longitude, double latitude) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + 21).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(4326);
        buffer.put((byte) 1).putInt(1).putDouble(longitude).putDouble(latitude);
        return buffer.array();
    }

    /** 将 GeoPackage 线路几何解码为 GeoJSON 文本。 */
    private String geometryGeoJson(byte[] gpkg) throws IOException {
        if (gpkg.length < 13 || gpkg[0] != 'G' || gpkg[1] != 'P') throw new IllegalArgumentException("GeoPackage 几何头无效");
        int flags = Byte.toUnsignedInt(gpkg[3]);
        int envelope = (flags >> 1) & 7;
        int envelopeBytes = switch (envelope) { case 1 -> 32; case 2, 3 -> 48; case 4 -> 64; default -> 0; };
        ByteBuffer buffer = ByteBuffer.wrap(gpkg).order((flags & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buffer.position(8 + envelopeBytes);
        JsonNode geoJson = readWkb(buffer);
        return objectMapper.writeValueAsString(geoJson);
    }

    /** 从 GeoPackage 点几何中读取经纬度。 */
    private double[] pointCoordinates(byte[] gpkg) {
        if (gpkg.length < 29 || gpkg[0] != 'G' || gpkg[1] != 'P') throw new IllegalArgumentException("途经站点几何格式无效");
        int flags = Byte.toUnsignedInt(gpkg[3]);
        int envelope = (flags >> 1) & 7;
        int envelopeBytes = switch (envelope) { case 1 -> 32; case 2, 3 -> 48; case 4 -> 64; default -> 0; };
        ByteBuffer buffer = ByteBuffer.wrap(gpkg).order((flags & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buffer.position(8 + envelopeBytes);
        int byteOrder = Byte.toUnsignedInt(buffer.get());
        buffer.order(byteOrder == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != 1) throw new IllegalArgumentException("途经站点图层只支持 POINT");
        return new double[]{buffer.getDouble(), buffer.getDouble()};
    }

    /** 递归解码 WKB LineString 或 MultiLineString。 */
    private JsonNode readWkb(ByteBuffer buffer) throws IOException {
        int byteOrder = Byte.toUnsignedInt(buffer.get());
        ByteOrder previous = buffer.order();
        buffer.order(byteOrder == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int type = buffer.getInt();
        JsonNode result;
        if (type == 2) {
            int count = buffer.getInt();
            var coordinates = objectMapper.createArrayNode();
            for (int index = 0; index < count; index++) {
                var point = objectMapper.createArrayNode();
                point.add(buffer.getDouble()); point.add(buffer.getDouble()); coordinates.add(point);
            }
            result = objectMapper.createObjectNode().put("type", "LineString").set("coordinates", coordinates);
        } else if (type == 5) {
            int count = buffer.getInt();
            var coordinates = objectMapper.createArrayNode();
            for (int index = 0; index < count; index++) {
                JsonNode line = readWkb(buffer);
                coordinates.add(line.get("coordinates"));
            }
            result = objectMapper.createObjectNode().put("type", "MultiLineString").set("coordinates", coordinates);
        } else {
            throw new IllegalArgumentException("轨迹图层只支持 LineString 或 MultiLineString");
        }
        buffer.order(previous);
        return result;
    }
}
