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

@Service
public class FootSpotService {
    private static final Logger log = LoggerFactory.getLogger(FootSpotService.class);
    private final FootSpotMapper mapper;
    private final GeoReferenceClient geoClient;
    private final ObjectMapper objectMapper;
    public FootSpotService(FootSpotMapper mapper, GeoReferenceClient geoClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.geoClient = geoClient;
        this.objectMapper = objectMapper;
    }

    public List<FootSpot> findAll(Integer year) {
        List<FootSpot> spots = mapper.selectRecords(year);
        spots.forEach(this::hydrate);
        enrichRegions(spots);
        return spots;
    }

    public Map<String, Object> findPage(String visitType, String keyword, String region, int page, int pageSize,
                                        String sortBy, String sortOrder) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        if (StringUtils.hasText(visitType) && !List.of("travel", "transit").contains(visitType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录性质无效");
        String type = StringUtils.hasText(visitType) ? visitType : null;
        String placeKeyword = like(keyword);
        String regionKeyword = region == null ? "" : region.trim().toLowerCase(Locale.ROOT);
        String sort = sortBy != null && List.of("placeName", "visitType", "visitDate").contains(sortBy) ? sortBy : "visitDate";
        String order = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        List<FootSpot> records;
        long total;
        if (regionKeyword.isEmpty()) {
            total = mapper.countRecords(type, placeKeyword);
            records = mapper.selectPage(type, placeKeyword, sort, order, pageSize, (page - 1L) * pageSize);
            records.forEach(this::hydrate);
            enrichRegions(records);
        } else {
            records = mapper.searchRecords(type, placeKeyword, sort, order);
            records.forEach(this::hydrate);
            enrichRegions(records);
            records = records.stream().filter(item -> item.getRegionName() != null
                    && item.getRegionName().toLowerCase(Locale.ROOT).contains(regionKeyword)).toList();
            total = records.size();
            int start = (int) Math.min((page - 1L) * pageSize, records.size());
            records = records.subList(start, Math.min(start + pageSize, records.size()));
        }
        return Map.of("records", records, "total", total, "page", page, "pageSize", pageSize);
    }

    public FootSpot findById(Long id) {
        FootSpot spot = mapper.selectRecordById(id);
        if (spot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        hydrate(spot);
        enrichRegions(List.of(spot));
        return spot;
    }

    private void enrichRegions(List<FootSpot> spots) {
        List<GeoReferenceClient.GeoPoint> points = spots.stream()
                .filter(item -> item.getLongitude() != null && item.getLatitude() != null)
                .map(item -> new GeoReferenceClient.GeoPoint(String.valueOf(item.getFootprintId()), item.getLongitude().doubleValue(), item.getLatitude().doubleValue()))
                .toList();
        Map<String, GeoReferenceClient.GeoLocation> regions = reverseGeocode(points);
        spots.forEach(item -> {
            GeoReferenceClient.GeoLocation location = regions.get(String.valueOf(item.getFootprintId()));
            List<GeoReferenceClient.GeoRegion> path = location == null || location.path() == null ? List.of() : location.path();
            GeoReferenceClient.GeoRegion matched = path.isEmpty() ? null : path.get(path.size() - 1);
            item.setRegionName(formatRegionPath(path));
            GeoReferenceClient.GeoRegion city = path.stream().filter(region -> region.level() != null && region.level() == 2)
                    .findFirst().orElse(matched != null && matched.level() != null && matched.level() > 0 ? matched : null);
            item.setCityName(city == null ? null : city.name());
            item.setCityRegionId(city == null ? null : city.id());
            item.setProvinceName(path.stream().filter(region -> region.level() != null && region.level() == 1)
                    .map(GeoReferenceClient.GeoRegion::name).findFirst().orElse(null));
            item.setRegionCode(matched == null ? null : matched.regionCode());
            item.setRegionId(matched == null ? null : matched.id());
        });
    }

    private String like(String value) {
        return StringUtils.hasText(value) ? "%" + value.trim() + "%" : null;
    }

    private String formatRegionPath(List<GeoReferenceClient.GeoRegion> path) {
        StringBuilder name = new StringBuilder();
        String previous = null;
        for (GeoReferenceClient.GeoRegion region : path) {
            if (region.level() == null || region.level() == 0 || !StringUtils.hasText(region.name())) continue;
            if (!region.name().equals(previous)) name.append(region.name());
            previous = region.name();
        }
        return name.toString();
    }

    private Map<String, GeoReferenceClient.GeoLocation> reverseGeocode(List<GeoReferenceClient.GeoPoint> points) {
        try {
            return geoClient.reverseGeocodeBatch(points);
        } catch (RestClientException exception) {
            log.warn("Mapserver unavailable; returning footprints without administrative regions", exception);
            return Map.of();
        }
    }

    @Transactional
    public FootSpot add(FootSpot spot) {
        validate(spot); prepare(spot); spot.setFootprintId(null);
        mapper.insert(spot); mapper.updateLocation(spot.getFootprintId(), spot.getLongitude(), spot.getLatitude());
        return hydrate(mapper.selectRecordById(spot.getFootprintId()));
    }

    @Transactional
    public FootSpot update(Long id, FootSpot spot) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        validate(spot); prepare(spot); spot.setFootprintId(id); mapper.updateById(spot); mapper.updateLocation(id, spot.getLongitude(), spot.getLatitude());
        return hydrate(mapper.selectRecordById(id));
    }

    public void delete(Long id) { if (mapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在"); }

    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的足迹记录");
        return mapper.delete(new LambdaQueryWrapper<FootSpot>().in(FootSpot::getFootprintId, validIds));
    }

    private void validate(FootSpot spot) {
        if (spot == null || !StringUtils.hasText(spot.getPlaceName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地点名称不能为空");
        if (!"transit".equals(spot.getVisitType())) spot.setVisitType("travel");
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

    private FootSpot hydrate(FootSpot spot) {
        if (spot == null) return null;
        try {
            spot.setMediaIds(spot.getMediaIdsJson() == null ? List.of()
                    : objectMapper.readValue(spot.getMediaIdsJson(), new TypeReference<>() {}));
            return spot;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("足迹图片列表数据损坏", e);
        }
    }

    private void prepare(FootSpot spot) {
        try {
            spot.setMediaIdsJson(objectMapper.writeValueAsString(spot.getMediaIds()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("足迹图片列表格式无效", e);
        }
    }
}
