package com.lifelog.travel.train;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/** 根据手动站点或外部时刻信息生成铁路线路轨迹。 */
@Service
public class TrainRouteSamplingService {
    private final SpiderTrainClient spiderClient;
    private final RailwayRouteClient railwayClient;
    private final ObjectMapper objectMapper;

    /** 创建铁路路线采样服务。 */
    public TrainRouteSamplingService(SpiderTrainClient spiderClient, RailwayRouteClient railwayClient, ObjectMapper objectMapper) {
        this.spiderClient = spiderClient;
        this.railwayClient = railwayClient;
        this.objectMapper = objectMapper;
    }

    /** 获取车次途经站、请求路线采样并返回可保存的轨迹信息。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sample(TrainRouteSampleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求内容不能为空");
        }
        boolean automatic = !"manual".equalsIgnoreCase(request.getMode());
        String trainNo = StringUtils.hasText(request.getTrainNo())
                ? request.getTrainNo().trim().toUpperCase() : "";
        if (automatic && trainNo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自动查询需要填写车次");
        }
        Map<String, Object> timetable = Map.of();
        List<String> stationNames;
        if (automatic) {
            try {
                timetable = spiderClient.stations(trainNo, LocalDate.now());
            } catch (RestClientException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "外部车次服务不可用，请手动填写有序途经站后生成轨迹", exception);
            }
            Object rawStations = timetable.get("stations");
            if (!(rawStations instanceof List<?> list) || list.size() < 2) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未查询到该车次的完整途经站");
            }
            stationNames = list.stream().filter(Map.class::isInstance)
                    .map(Map.class::cast).map(item -> item.get("name"))
                    .filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText).toList();
        } else {
            stationNames = request.getStations() == null ? List.of() : request.getStations().stream()
                    .filter(StringUtils::hasText).map(String::trim).toList();
            if (stationNames.size() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少填写两个有序途经站");
        }

        Map<String, Object> sampled;
        try {
            sampled = railwayClient.sample(trainNo, stationNames);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "地图服务轨迹计算失败", exception);
        }
        Map<String, Object> result = new LinkedHashMap<>(sampled);
        try {
            result.put("routeGeoJson", objectMapper.writeValueAsString(sampled.get("geometry")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("轨迹序列化失败", exception);
        }
        result.put("source", automatic ? "spider+railway-network" : "manual+railway-network");
        result.put("trainType", timetable.get("train_type"));
        result.put("timetable", timetable.getOrDefault("stations", new ArrayList<>()));
        result.remove("geometry");
        return result;
    }
}
