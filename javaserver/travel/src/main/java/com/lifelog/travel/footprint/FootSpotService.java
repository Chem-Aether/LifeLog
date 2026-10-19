package com.lifelog.travel.footprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.travel.integration.mapserver.GeoReferenceClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** 处理足迹查询、行政区缓存和媒体数据转换及记录维护。 */
@Service
public class FootSpotService {
    private static final Logger log = LoggerFactory.getLogger(FootSpotService.class);
    private final FootSpotMapper mapper;
    private final GeoReferenceClient geoClient;
    private final ObjectMapper objectMapper;

    /** 创建足迹业务服务。 */
    public FootSpotService(FootSpotMapper mapper, GeoReferenceClient geoClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.geoClient = geoClient;
        this.objectMapper = objectMapper;
    }

    /** 查询所有匹配足迹并补充媒体和行政区路径。 */
    public List<FootSpot> findAll(FootSpotQueryDTO query) {
        validateVisitType(query.getVisitType());
        String type = StringUtils.hasText(query.getVisitType()) ? query.getVisitType() : null;
        String locationType = StringUtils.hasText(query.getLocationType()) ? query.getLocationType() : null;
        String sort = sortColumn(query.getSortBy());
        String order = normalizeSortOrder(query.getSortOrder());
        String regionKeyword = query.getRegion() == null ? "" : query.getRegion().trim().toLowerCase(Locale.ROOT);
        List<FootSpot> spots = mapper.searchRecords(type, locationType, like(query.getKeyword()), query.getYear(), sort, order);
        spots.forEach(this::hydrate);
        enrichCachedRegions(spots);
        return spots.stream().filter(item -> regionKeyword.isEmpty()
                || formatRegionPath(item.getRegionPaths()).toLowerCase(Locale.ROOT).contains(regionKeyword)).toList();
    }

    /** 按条件分页查询足迹，并在行政区服务不可用时保留基本记录数据。 */
    public Map<String, Object> findPage(FootSpotQueryDTO query) {
        String visitType = query.getVisitType();
        String locationType = StringUtils.hasText(query.getLocationType()) ? query.getLocationType() : null;
        String keyword = query.getKeyword();
        String region = query.getRegion();
        Integer requestedPage = query.getPage();
        Integer requestedPageSize = query.getPageSize();
        String sortBy = query.getSortBy();
        String sortOrder = query.getSortOrder();
        if (requestedPage == null || requestedPageSize == null || requestedPage < 1
                || requestedPageSize < 1 || requestedPageSize > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        int page = requestedPage;
        int pageSize = requestedPageSize;
        validateVisitType(visitType);
        String type = StringUtils.hasText(visitType) ? visitType : null;
        String placeKeyword = like(keyword);
        Integer year = query.getYear();
        String regionKeyword = region == null ? "" : region.trim().toLowerCase(Locale.ROOT);
        String sort = sortColumn(sortBy);
        String order = normalizeSortOrder(sortOrder);
        List<FootSpot> records;
        long total;
        if (regionKeyword.isEmpty()) {
            total = mapper.countRecords(type, locationType, placeKeyword, year);
            records = mapper.selectPage(type, locationType, placeKeyword, year, sort, order, pageSize, (page - 1L) * pageSize);
            records.forEach(this::hydrate);
            enrichCachedRegions(records);
        } else {
            records = mapper.searchRecords(type, locationType, placeKeyword, year, sort, order);
            records.forEach(this::hydrate);
            enrichCachedRegions(records);
            records = records.stream().filter(item -> formatRegionPath(item.getRegionPaths()).toLowerCase(Locale.ROOT)
                    .contains(regionKeyword)).toList();
            total = records.size();
            int start = (int) Math.min((page - 1L) * pageSize, records.size());
            records = records.subList(start, Math.min(start + pageSize, records.size()));
        }
        return Map.of("records", records, "total", total, "page", page, "pageSize", pageSize);
    }

    /** 查询单条足迹并补全关联信息。 */
    public FootSpot findById(Long id) {
        FootSpot spot = mapper.selectRecordById(id);
        if (spot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        }
        hydrate(spot);
        enrichCachedRegions(List.of(spot));
        return spot;
    }

    /** 按足迹缓存的行政区 ID 批量补充结构化路径。 */
    private void enrichCachedRegions(List<FootSpot> spots) {
        Map<String, List<RegionPathNodeDTO>> pathsById = findRegionPathsForIds(spots.stream()
                .map(FootSpot::getRegionId).filter(StringUtils::hasText).distinct().toList());
        spots.forEach(item -> {
            item.setRegionPaths(pathsById.getOrDefault(item.getRegionId(), List.of()));
        });
    }

    private Map<String, List<RegionPathNodeDTO>> findRegionPathsForIds(List<String> ids) {
        Map<String, List<RegionPathNodeDTO>> pathsById = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            try {
                Map<String, GeoReferenceClient.GeoPath> paths = geoClient.regionPaths(ids.subList(start, Math.min(start + 500, ids.size())));
                paths.forEach((id, result) -> {
                    List<RegionPathNodeDTO> path = result.path() == null ? List.of() : result.path().stream()
                            .map(region -> new RegionPathNodeDTO(region.id(), region.name(), region.level())).toList();
                    pathsById.put(id, path);
                });
            } catch (RestClientException exception) {
                log.warn("Mapserver unavailable; returning footprints without administrative paths", exception);
                return pathsById;
            }
        }
        return pathsById;
    }

    private String deepestRegionId(GeoReferenceClient.GeoLocation location) {
        if (location == null || location.path() == null || location.path().isEmpty()) return null;
        return location.path().get(location.path().size() - 1).id();
    }

    /** 将非空关键字转换为 SQL 模糊匹配形式。 */
    private String like(String value) {
        return StringUtils.hasText(value) ? "%" + value.trim() + "%" : null;
    }

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

    /** 按行政区路径顺序拼接可读名称。 */
    private String formatRegionPath(List<RegionPathNodeDTO> path) {
        StringBuilder name = new StringBuilder();
        String previous = null;
        for (RegionPathNodeDTO region : path) {
            if (region.level() == null || region.level() == 0 || !StringUtils.hasText(region.name())) continue;
            if (!region.name().equals(previous)) name.append(region.name());
            previous = region.name();
        }
        return name.toString();
    }

    /** 批量反查坐标；mapserver 不可用时返回空结果。 */
    private Map<String, GeoReferenceClient.GeoLocation> reverseGeocode(List<GeoReferenceClient.GeoPoint> points) {
        try {
            return geoClient.reverseGeocodeBatch(points);
        } catch (RestClientException exception) {
            log.warn("Mapserver unavailable; returning footprints without administrative regions", exception);
            return Map.of();
        }
    }

    /** 校验并创建足迹记录及其空间位置。 */
    @Transactional
    public FootSpot add(FootSpot spot) {
        validate(spot); prepare(spot); spot.setFootprintId(null); updateRegionCache(spot);
        mapper.insert(spot); mapper.updateLocation(spot.getFootprintId(), spot.getLongitude(), spot.getLatitude());
        return findById(spot.getFootprintId());
    }

    /** 校验并更新足迹记录及其空间位置。 */
    @Transactional
    public FootSpot update(Long id, FootSpot spot) {
        FootSpot existing = mapper.selectRecordById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        }
        validate(spot); prepare(spot); 
        boolean coordinatesChanged = !Objects.equals(existing.getLongitude(), spot.getLongitude())
                || !Objects.equals(existing.getLatitude(), spot.getLatitude());
        spot.setFootprintId(id);
        if (coordinatesChanged) updateRegionCache(spot);
        else spot.setRegionId(existing.getRegionId());
        mapper.updateById(spot); 
        mapper.updateLocation(id, spot.getLongitude(), spot.getLatitude());
        return findById(id);
    }

    private void updateRegionCache(FootSpot spot) {
        if (spot.getLongitude() == null || spot.getLatitude() == null) {
            spot.setRegionId(null);
            return;
        }
        Map<String, GeoReferenceClient.GeoLocation> matches = reverseGeocode(List.of(new GeoReferenceClient.GeoPoint(
                "footprint", spot.getLongitude().doubleValue(), spot.getLatitude().doubleValue())));
        if (matches.containsKey("footprint")) {
            spot.setRegionId(deepestRegionId(matches.get("footprint")));
        } else {
            spot.setRegionId(null);
        }
    }

    /** 删除指定足迹。 */
    public void delete(Long id) { 
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在"); 
        }
    }

    /** 批量删除去重后的有效足迹 ID。 */
    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的足迹记录");
        }
        return mapper.delete(new LambdaQueryWrapper<FootSpot>().in(FootSpot::getFootprintId, validIds));
    }

    /** 校验足迹必填项、坐标范围和媒体数量。 */
    private void validate(FootSpot spot) {
        if (spot == null || !StringUtils.hasText(spot.getPlaceName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地点名称不能为空");
        if (!"travel".equals(spot.getVisitType()) && !"transit".equals(spot.getVisitType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录性质必须是 travel 或 transit");
        }
        boolean longitude = spot.getLongitude() != null, latitude = spot.getLatitude() != null;
        if (longitude != latitude) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "经纬度必须同时填写或同时留空");
        if (longitude && (spot.getLongitude().doubleValue() < -180 || spot.getLongitude().doubleValue() > 180
                || spot.getLatitude().doubleValue() < -90 || spot.getLatitude().doubleValue() > 90))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "经纬度超出有效范围");
        List<String> mediaIds = spot.getMediaIds() == null ? List.of() : spot.getMediaIds().stream()
                .filter(Objects::nonNull).map(String::trim).filter(StringUtils::hasText).distinct().toList();
        if (mediaIds.size() > 9) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每条足迹最多上传 9 张图片");
        spot.setMediaIds(mediaIds);
    }

    /** 将 JSONB 媒体字段解析为结构化 ID 列表。 */
    private FootSpot hydrate(FootSpot spot) {
        if (spot == null) return null;
        try {
            spot.setMediaIds(spot.getMediaIdsJson() == null ? List.of()
                    : objectMapper.readValue(spot.getMediaIdsJson(), new TypeReference<>() {}));
            return spot;
        } 
        catch (JsonProcessingException e) {
            throw new IllegalStateException("足迹图片列表数据损坏", e);
        }
    }

    /** 将媒体 ID 列表序列化为 JSONB 存储值。 */
    private void prepare(FootSpot spot) {
        try {
            spot.setMediaIdsJson(objectMapper.writeValueAsString(spot.getMediaIds()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("足迹图片列表格式无效", e);
        }
    }
}
