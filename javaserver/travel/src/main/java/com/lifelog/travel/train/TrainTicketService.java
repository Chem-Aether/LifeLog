package com.lifelog.travel.train;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 处理铁路行程及途经站的查询、校验、持久化和里程计算。 */
@Service
public class TrainTicketService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private final TrainRecordMapper mapper;
    private final TrainWaypointMapper waypointMapper;
    private final ObjectMapper objectMapper;

    /** 创建铁路行程业务服务。 */
    public TrainTicketService(TrainRecordMapper mapper, TrainWaypointMapper waypointMapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.waypointMapper = waypointMapper;
        this.objectMapper = objectMapper;
    }

    /** 按条件分页查询铁路行程并加载途经站。 */
    public Page<TrainRecord> list(TrainRecordQueryDTO dto) {
        LambdaQueryWrapper<TrainRecord> query = new LambdaQueryWrapper<>();
        query.like(StringUtils.hasText(dto.getTrainNo()), TrainRecord::getTrainNo, dto.getTrainNo());
        query.like(StringUtils.hasText(dto.getStartStationName()), TrainRecord::getStartStationName, dto.getStartStationName());
        query.like(StringUtils.hasText(dto.getEndStationName()), TrainRecord::getEndStationName, dto.getEndStationName());
        query.ge(dto.getDepartureTimeStart() != null, TrainRecord::getDepartureTime, dto.getDepartureTimeStart());
        query.le(dto.getDepartureTimeEnd() != null, TrainRecord::getDepartureTime, dto.getDepartureTimeEnd());
        query.orderByDesc(TrainRecord::getDepartureTime);
        long page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        long size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : Math.min(dto.getPageSize(), 100);
        Page<TrainRecord> result = mapper.selectPage(new Page<>(page, size), query);
        result.getRecords().replaceAll(record -> hydrate(mapper.selectRecordById(record.getTrainId())));
        return result;
    }

    /** 查询铁路行程详情及途经站。 */
    public TrainRecord get(Long id) {
        TrainRecord record = hydrate(mapper.selectRecordById(id));
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "列车记录不存在");
        return record;
    }

    /** 校验并事务性保存行程、线路和途经站。 */
    @Transactional
    public TrainRecord save(TrainRecord record) {
        validate(record);
        prepare(record);
        record.setTrainId(null);
        mapper.insert(record);
        mapper.updateRoute(record.getTrainId(), record.getRouteGeoJson());
        replaceWaypoints(record.getTrainId(), record.getWaypoints());
        return get(record.getTrainId());
    }

    /** 校验并事务性更新行程、线路和途经站。 */
    @Transactional
    public TrainRecord update(Long id, TrainRecord record) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "列车记录不存在");
        validate(record);
        prepare(record);
        record.setTrainId(id);
        mapper.updateById(record);
        mapper.updateRoute(id, record.getRouteGeoJson());
        replaceWaypoints(id, record.getWaypoints());
        return get(id);
    }

    /** 删除指定铁路行程。 */
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "列车记录不存在");
    }

    /** 批量删除去重后的有效行程 ID。 */
    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的铁路行程");
        return mapper.delete(new LambdaQueryWrapper<TrainRecord>().in(TrainRecord::getTrainId, validIds));
    }

    /** 为行程记录加载其途经站列表。 */
    public TrainRecord hydrate(TrainRecord record) {
        if (record == null) return null;
        record.setWaypoints(waypointMapper.selectByTrainId(record.getTrainId()));
        return record;
    }

    /** 规范化途经站顺序和线路几何，并在缺少里程时计算里程。 */
    private void prepare(TrainRecord record) {
        if (record.getWaypoints() == null) record.setWaypoints(java.util.List.of());
        for (int i = 0; i < record.getWaypoints().size(); i++) record.getWaypoints().get(i).setSequence(i + 1);
        record.setRouteGeoJson(normalizeRouteGeoJson(record.getRouteGeoJson()));
        updateMileage(record);
    }

    /** 优先根据线路几何计算里程，缺失时使用途经站坐标计算。 */
    private void updateMileage(TrainRecord record) {
        if (record.getMileageKm() != null && record.getMileageKm().signum() > 0) return;
        Double routeDistance = routeDistanceKm(record.getRouteGeoJson());
        if (routeDistance != null) {
            record.setMileageKm(BigDecimal.valueOf(routeDistance).setScale(2, RoundingMode.HALF_UP));
            return;
        }
        Double waypointDistance = waypointDistanceKm(record.getWaypoints());
        if (waypointDistance != null) {
            record.setMileageKm(BigDecimal.valueOf(waypointDistance).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** 从 LineString 或 MultiLineString GeoJSON 计算总里程。 */
    private Double routeDistanceKm(String routeGeoJson) {
        if (!StringUtils.hasText(routeGeoJson)) return null;
        try {
            JsonNode geometry = objectMapper.readTree(routeGeoJson);
            JsonNode coordinates = geometry.path("coordinates");
            if ("LineString".equals(geometry.path("type").asText())) return lineDistanceKm(coordinates);
            if ("MultiLineString".equals(geometry.path("type").asText()) && coordinates.isArray()) {
                double total = 0;
                boolean hasSegment = false;
                for (JsonNode line : coordinates) {
                    Double distance = lineDistanceKm(line);
                    if (distance != null) { total += distance; hasSegment = true; }
                }
                return hasSegment ? total : null;
            }
            throw invalidRoute();
        } catch (JsonProcessingException exception) {
            throw invalidRoute();
        }
    }

    /** 根据线路坐标序列计算球面距离。 */
    private Double lineDistanceKm(JsonNode coordinates) {
        if (!coordinates.isArray()) throw invalidRoute();
        JsonNode previous = null;
        double total = 0;
        int segments = 0;
        for (JsonNode coordinate : coordinates) {
            if (!coordinate.isArray() || coordinate.size() < 2) throw invalidRoute();
            double longitude = coordinate.get(0).asDouble(Double.NaN);
            double latitude = coordinate.get(1).asDouble(Double.NaN);
            validateCoordinate(longitude, latitude);
            if (previous != null) {
                total += greatCircleDistance(previous.get(0).asDouble(), previous.get(1).asDouble(), longitude, latitude);
                segments++;
            }
            previous = coordinate;
        }
        return segments == 0 ? null : total;
    }

    /** 根据有序且具备坐标的途经站计算总里程。 */
    private Double waypointDistanceKm(List<TrainWaypoint> points) {
        List<TrainWaypoint> ordered = points.stream()
                .filter(point -> point.getLongitude() != null && point.getLatitude() != null)
                .sorted(Comparator.comparing(TrainWaypoint::getSequence, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (ordered.size() < 2) return null;
        double total = 0;
        for (int index = 1; index < ordered.size(); index++) {
            TrainWaypoint previous = ordered.get(index - 1);
            TrainWaypoint current = ordered.get(index);
            double lon1 = previous.getLongitude().doubleValue();
            double lat1 = previous.getLatitude().doubleValue();
            double lon2 = current.getLongitude().doubleValue();
            double lat2 = current.getLatitude().doubleValue();
            validateCoordinate(lon1, lat1);
            validateCoordinate(lon2, lat2);
            total += greatCircleDistance(lon1, lat1, lon2, lat2);
        }
        return total;
    }

    /** 检查经纬度是否为有限值且处于有效范围。 */
    private void validateCoordinate(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹或途经站坐标超出经纬度有效范围");
        }
    }

    /** 使用 Haversine 公式计算两点之间的球面距离。 */
    private double greatCircleDistance(double lon1, double lat1, double lon2, double lat2) {
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lon2 - lon1);
        double startLatitude = Math.toRadians(lat1);
        double endLatitude = Math.toRadians(lat2);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    /** 删除旧站点并按顺序写入新的途经站列表。 */
    private void replaceWaypoints(Long trainId, java.util.List<TrainWaypoint> waypoints) {
        waypointMapper.deleteByTrainId(trainId);
        for (int index = 0; index < waypoints.size(); index++) {
            TrainWaypoint waypoint = waypoints.get(index);
            if (!StringUtils.hasText(waypoint.getStationName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "途经站名称不能为空");
            }
            waypoint.setWaypointId(null);
            waypoint.setTrainId(trainId);
            waypoint.setSequence(index + 1);
            waypointMapper.insertRecord(waypoint);
        }
    }

    /** 验证并规范化铁路线路 GeoJSON。 */
    private String normalizeRouteGeoJson(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            JsonNode root = objectMapper.readTree(value.trim());
            if (root.isTextual()) root = objectMapper.readTree(root.textValue());

            String geometryType = root.path("type").asText();
            if (!"LineString".equals(geometryType) && !"MultiLineString".equals(geometryType)) {
                throw invalidRoute();
            }
            JsonNode coordinates = root.path("coordinates");
            if (!coordinates.isArray() || coordinates.isEmpty()) throw invalidRoute();
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw invalidRoute();
        }
    }

    /** 创建统一的线路几何参数错误。 */
    private ResponseStatusException invalidRoute() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "轨迹必须是 LineString 或 MultiLineString Geometry");
    }

    /** 校验车次、起终点及时间先后关系。 */
    private void validate(TrainRecord record) {
        if (record == null || !StringUtils.hasText(record.getTrainNo()) || !StringUtils.hasText(record.getStartStationName())
                || !StringUtils.hasText(record.getEndStationName()) || record.getDepartureTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "车次、起终点和发车时间不能为空");
        }
        if (record.getArrivalTime() != null && record.getArrivalTime().isBefore(record.getDepartureTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "到达时间不能早于发车时间");
        }
    }
}
