package com.lifelog.travel.footprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.travel.integration.mapserver.GeoReferenceClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class FootSpotService {
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
        List<GeoReferenceClient.GeoPoint> points = spots.stream()
                .filter(item -> item.getLongitude() != null && item.getLatitude() != null)
                .map(item -> new GeoReferenceClient.GeoPoint(String.valueOf(item.getFootprintId()), item.getLongitude().doubleValue(), item.getLatitude().doubleValue()))
                .toList();
        Map<String, GeoReferenceClient.GeoLocation> regions = geoClient.reverseGeocodeBatch(points);
        spots.forEach(item -> {
            GeoReferenceClient.GeoLocation location = regions.get(String.valueOf(item.getFootprintId()));
            item.setRegionName(location == null || location.formattedRegion() == null ? "" : location.formattedRegion());
            if (location != null) {
                GeoReferenceClient.GeoRegion region = location.district() != null ? location.district() : location.city();
                item.setRegionCode(region == null ? null : region.code());
            }
        });
        return spots;
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
