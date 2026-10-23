package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 处理足迹查询、行政区编码筛选、媒体数据转换及记录维护。 */
@Service
public class FootSpotService {
    private final FootSpotMapper mapper;
    private final ObjectMapper objectMapper;

    public FootSpotService(FootSpotMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** 查询全部符合条件的足迹；行政区路径由调用方按编码查询。 */
    public List<FootSpot> findAll(FootSpotQueryDTO query) {
        validateVisitType(query.getVisitType());
        List<FootSpot> spots = mapper.searchRecords(type(query.getVisitType()), nullable(query.getLocationType()),
                like(query.getKeyword()), query.getYear(), null, sortColumn(query.getSortBy()), normalizeSortOrder(query.getSortOrder()));
        spots.forEach(this::hydrate);
        return spots;
    }

    /** 分页查询足迹；行政区路径和名称解析由调用方负责。 */
    public Map<String, Object> findPage(FootSpotQueryDTO query) {
        Integer page = query.getPage();
        Integer pageSize = query.getPageSize();
        if (page == null || pageSize == null || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        }
        validateVisitType(query.getVisitType());
        String type = type(query.getVisitType());
        String locationType = nullable(query.getLocationType());
        String keyword = like(query.getKeyword());
        List<String> regionIds = query.getRegionIds();
        long total = mapper.countRecords(type, locationType, keyword, regionIds, query.getYear());
        List<FootSpot> records = mapper.selectPage(type, locationType, keyword, query.getYear(), regionIds,
                sortColumn(query.getSortBy()), normalizeSortOrder(query.getSortOrder()), pageSize, (page - 1L) * pageSize);
        records.forEach(this::hydrate);
        return Map.of("records", records, "total", total, "page", page, "pageSize", pageSize);
    }

    /** 返回符合其他筛选条件的不同行政区编码，供前端批量解析路径。 */
    public List<String> findRegionIds(FootSpotQueryDTO query) {
        validateVisitType(query.getVisitType());
        return mapper.selectRegionIds(type(query.getVisitType()), nullable(query.getLocationType()),
                like(query.getKeyword()), query.getYear());
    }

    public FootSpot findById(Long id) {
        FootSpot spot = mapper.selectRecordById(id);
        if (spot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        return hydrate(spot);
    }

    private String type(String value) { return StringUtils.hasText(value) ? value : null; }
    private String nullable(String value) { return StringUtils.hasText(value) ? value : null; }
    private String like(String value) { return StringUtils.hasText(value) ? "%" + value.trim() + "%" : null; }

    private void validateVisitType(String visitType) {
        if (StringUtils.hasText(visitType) && !List.of("travel", "transit").contains(visitType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录性质无效");
        }
    }

    private String sortColumn(String sortBy) {
        return sortBy != null && List.of("placeName", "visitType", "visitDate", "locationType").contains(sortBy)
                ? sortBy : "visitDate";
    }

    private String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
    }

    @Transactional
    public FootSpot add(FootSpot spot) {
        validate(spot);
        prepare(spot);
        spot.setFootprintId(null);
        if (spot.getLongitude() == null) spot.setRegionId(null);
        mapper.insert(spot);
        mapper.updateLocation(spot.getFootprintId(), spot.getLongitude(), spot.getLatitude());
        return findById(spot.getFootprintId());
    }

    @Transactional
    public FootSpot update(Long id, FootSpot spot) {
        FootSpot existing = mapper.selectRecordById(id);
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        validate(spot);
        prepare(spot);
        boolean coordinatesChanged = !Objects.equals(existing.getLongitude(), spot.getLongitude())
                || !Objects.equals(existing.getLatitude(), spot.getLatitude());
        spot.setFootprintId(id);
        if (!coordinatesChanged) spot.setRegionId(existing.getRegionId());
        else if (spot.getLongitude() == null) spot.setRegionId(null);
        mapper.updateById(spot);
        mapper.updateLocation(id, spot.getLongitude(), spot.getLatitude());
        return findById(id);
    }

    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
    }

    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的足迹记录");
        return mapper.delete(new LambdaQueryWrapper<FootSpot>().in(FootSpot::getFootprintId, validIds));
    }

    private void validate(FootSpot spot) {
        if (spot == null || !StringUtils.hasText(spot.getPlaceName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地点名称不能为空");
        }
        if (!"travel".equals(spot.getVisitType()) && !"transit".equals(spot.getVisitType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录性质必须是 travel 或 transit");
        }
        boolean longitude = spot.getLongitude() != null;
        boolean latitude = spot.getLatitude() != null;
        if (longitude != latitude) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "经纬度必须同时填写或同时留空");
        if (longitude && (spot.getLongitude().doubleValue() < -180 || spot.getLongitude().doubleValue() > 180
                || spot.getLatitude().doubleValue() < -90 || spot.getLatitude().doubleValue() > 90)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "经纬度超出有效范围");
        }
        if (StringUtils.hasText(spot.getRegionId()) && spot.getRegionId().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行政区 ID 长度无效");
        }
        List<String> mediaUrls = spot.getMediaUrls() == null ? List.of() : spot.getMediaUrls().stream()
                .filter(Objects::nonNull).map(String::trim).filter(StringUtils::hasText).distinct().toList();
        if (mediaUrls.size() > 9) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每条足迹最多上传 9 张图片");
        if (mediaUrls.stream().anyMatch(url -> url.length() > 2048 || !(url.startsWith("http://") || url.startsWith("https://")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "媒体 URL 无效");
        }
        spot.setMediaUrls(mediaUrls);
    }

    private FootSpot hydrate(FootSpot spot) {
        try {
            spot.setMediaUrls(spot.getMediaUrlsJson() == null ? List.of()
                    : objectMapper.readValue(spot.getMediaUrlsJson(), new TypeReference<>() {}));
            return spot;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("足迹图片列表数据损坏", exception);
        }
    }

    private void prepare(FootSpot spot) {
        try {
            spot.setMediaUrlsJson(objectMapper.writeValueAsString(spot.getMediaUrls()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("足迹图片列表格式无效", exception);
        }
    }
}
