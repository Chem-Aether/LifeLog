package com.lifelog.travel.footprint;

import com.lifelog.travel.integration.mapserver.GeoReferenceClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifelog.travel.footprint.FootSpot;
import com.lifelog.travel.footprint.FootSpotMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class FootSpotService {
    private final FootSpotMapper mapper;
    private final GeoReferenceClient geoClient;
    public FootSpotService(FootSpotMapper mapper, GeoReferenceClient geoClient) { this.mapper = mapper; this.geoClient = geoClient; }

    public List<FootSpot> findAll(Integer year) {
        List<FootSpot> spots = mapper.selectRecords(year);
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

    public FootSpot add(FootSpot spot) {
        validate(spot); spot.setFootprintId(null);
        mapper.insert(spot); mapper.updateLocation(spot.getFootprintId(), spot.getLongitude(), spot.getLatitude());
        return mapper.selectRecordById(spot.getFootprintId());
    }

    public FootSpot update(Long id, FootSpot spot) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "足迹记录不存在");
        validate(spot); spot.setFootprintId(id); mapper.updateById(spot); mapper.updateLocation(id, spot.getLongitude(), spot.getLatitude());
        return mapper.selectRecordById(id);
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
    }
}
