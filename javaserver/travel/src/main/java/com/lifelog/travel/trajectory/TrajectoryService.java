package com.lifelog.travel.trajectory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 处理轨迹采样、展示几何和记录摘要。 */
@Service
public class TrajectoryService {
    private static final int MAX_POINTS = 200_000;
    private final TrajectoryMapper mapper;
    private final TrajectoryPointMapper pointMapper;

    public TrajectoryService(TrajectoryMapper mapper, TrajectoryPointMapper pointMapper) {
        this.mapper = mapper;
        this.pointMapper = pointMapper;
    }

    public java.util.Map<String, Object> findPage(TrajectoryQueryDTO query) {
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getPageSize() == null ? 10 : query.getPageSize();
        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
        LocalDate date = query.getDate();
        String sortBy = sortColumn(query.getSortBy());
        String sortOrder = "asc".equalsIgnoreCase(query.getSortOrder()) ? "ASC" : "DESC";
        List<Trajectory> records = mapper.selectPage(query.getKeyword(), query.getActivityType(), date,
                sortBy, sortOrder, size, (page - 1L) * size);
        long total = mapper.countPage(query.getKeyword(), query.getActivityType(), date);
        return java.util.Map.of("records", records, "total", total, "page", page, "pageSize", size);
    }

    private String sortColumn(String requested) {
        return switch (requested == null ? "" : requested) {
            case "name" -> "name";
            case "activityType" -> "activity_type";
            case "durationS" -> "duration_s";
            case "distanceM" -> "distance_m";
            case "pointCount" -> "point_count";
            default -> "started_at";
        };
    }

    public List<Trajectory> findMapRoutes(Double west, Double south, Double east, Double north, Double zoom, Integer year) {
        if (west == null || south == null || east == null || north == null || zoom == null
                || west < -180 || east > 180 || south < -90 || north > 90 || west >= east || south >= north) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地图范围参数无效");
        }
        if (zoom < 8.5) return List.of();
        return mapper.selectMapRoutes(west, south, east, north, zoom, year);
    }

    public Trajectory findById(Long id) {
        Trajectory trajectory = mapper.selectWithRoute(id);
        if (trajectory == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "轨迹不存在");
        trajectory.setPoints(pointMapper.selectByTrajectoryId(id));
        return trajectory;
    }

    @Transactional
    public Trajectory save(Trajectory trajectory) {
        prepare(trajectory);
        trajectory.setTrajectoryId(null);
        mapper.insert(trajectory);
        persistPoints(trajectory);
        return findById(trajectory.getTrajectoryId());
    }

    @Transactional
    public Trajectory update(Long id, Trajectory trajectory) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "轨迹不存在");
        prepare(trajectory);
        trajectory.setTrajectoryId(id);
        mapper.updateById(trajectory);
        pointMapper.deleteByTrajectoryId(id);
        persistPoints(trajectory);
        return findById(id);
    }

    private void persistPoints(Trajectory trajectory) {
        List<TrajectoryPoint> points = trajectory.getPoints();
        for (int start = 0; start < points.size(); start += 1000) {
            pointMapper.insertBatch(trajectory.getTrajectoryId(), points.subList(start, Math.min(start + 1000, points.size())));
        }
        mapper.rebuildRoute(trajectory.getTrajectoryId());
    }

    private void prepare(Trajectory trajectory) {
        if (trajectory == null || !StringUtils.hasText(trajectory.getName()) || !StringUtils.hasText(trajectory.getActivityType())
                || trajectory.getStartedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹名称、活动类型和开始时间不能为空");
        }
        List<TrajectoryPoint> points = trajectory.getPoints();
        if (points == null || points.size() < 2 || points.size() > MAX_POINTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹点数量必须为 2 到 " + MAX_POINTS);
        }
        for (int index = 0; index < points.size(); index++) {
            TrajectoryPoint point = points.get(index);
            if (point.getRecordedAt() == null || point.getAltitudeM() == null || point.getLongitude() == null || point.getLatitude() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹点必须包含时间、经纬度和海拔");
            }
            if (!Double.isFinite(point.getLongitude()) || !Double.isFinite(point.getLatitude()) || !Double.isFinite(point.getAltitudeM())
                    || point.getLongitude() < -180 || point.getLongitude() > 180 || point.getLatitude() < -90 || point.getLatitude() > 90) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹点坐标无效");
            }
            if (point.getSegmentNo() == null) point.setSegmentNo(index == 0 ? 1 : points.get(index - 1).getSegmentNo());
            if (point.getSegmentNo() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹分段编号无效");
            if (index > 0 && point.getRecordedAt().isBefore(points.get(index - 1).getRecordedAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "轨迹点时间必须按顺序递增");
            }
            point.setSequence(index + 1);
        }
        java.util.Map<Integer, Long> segmentSizes = points.stream().collect(java.util.stream.Collectors.groupingBy(
                TrajectoryPoint::getSegmentNo, java.util.stream.Collectors.counting()));
        if (segmentSizes.values().stream().anyMatch(count -> count < 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每个轨迹分段至少需要两个采样点");
        }
        TrajectoryPoint last = points.getLast();
        if (trajectory.getEndedAt() == null) trajectory.setEndedAt(last.getRecordedAt());
        if (trajectory.getEndedAt().isBefore(trajectory.getStartedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        if (trajectory.getEndedAt().isBefore(last.getRecordedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束时间不能早于最后一个采样点");
        }
        trajectory.setDurationS((int) Math.min(Integer.MAX_VALUE,
                Duration.between(trajectory.getStartedAt(), trajectory.getEndedAt()).toSeconds()));
        trajectory.setDistanceM(calculateDistance(points));
        double gain = 0, loss = 0;
        for (int index = 1; index < points.size(); index++) {
            double delta = points.get(index).getAltitudeM() - points.get(index - 1).getAltitudeM();
            if (delta > 0) gain += delta; else loss -= delta;
        }
        trajectory.setElevationGainM(gain);
        trajectory.setElevationLossM(loss);
        trajectory.setPointCount(points.size());
    }

    private double calculateDistance(List<TrajectoryPoint> points) {
        double total = 0;
        for (int index = 1; index < points.size(); index++) {
            TrajectoryPoint a = points.get(index - 1), b = points.get(index);
            double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
            double dLat = lat2 - lat1, dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
            double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
            if (Objects.equals(a.getSegmentNo(), b.getSegmentNo())) {
                total += 6_371_008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
            }
        }
        return total;
    }

    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "轨迹不存在");
    }

    public int deleteBatch(List<Long> ids) {
        List<Long> valid = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (valid.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要删除的轨迹");
        return mapper.delete(new LambdaQueryWrapper<Trajectory>().in(Trajectory::getTrajectoryId, valid));
    }
}
