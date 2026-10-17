package com.lifelog.travel.integration.mapserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 调用 mapserver 查询机场信息和坐标对应的行政区路径。 */
@Component
public class GeoReferenceClient {

    private final RestClient restClient;

    /**
     * 创建 mapserver HTTP 客户端。
     * @param baseUrl mapserver 服务基础地址
     */
    public GeoReferenceClient(@Value("${mapserver.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    /**
     * 按 ICAO 代码批量查询机场信息。
     * @param codes ICAO 代码集合
     * @return 按 ICAO 代码索引的机场信息
     */
    public Map<String, GeoAirport> airportsByIcao(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyMap();
        ApiResponse<List<GeoAirport>> response = restClient.post()
                .uri("/api/airports/by-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("codes", codes))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return data(response).stream().collect(Collectors.toMap(GeoAirport::icao, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量反向地理编码坐标点。
     * @param points 待查询的坐标点
     * @return 按点 ID 索引的地理位置及行政区路径
     */
    public Map<String, GeoLocation> reverseGeocodeBatch(Collection<GeoPoint> points) {
        if (points == null || points.isEmpty()) return Collections.emptyMap();
        ApiResponse<List<GeoLocation>> response = restClient.post()
                .uri("/api/regions/reverse/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return data(response).stream().filter(item -> item.id() != null)
                .collect(Collectors.toMap(GeoLocation::id, Function.identity(), (left, right) -> left));
    }

    /** 按缓存的行政区 ID 批量查询行政区祖先路径。 */
    public Map<String, GeoPath> regionPaths(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        ApiResponse<Map<String, GeoPath>> response = restClient.post()
                .uri("/api/regions/paths")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("ids", ids))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response == null || response.data() == null ? Map.of() : response.data();
    }

    /**
     * 从统一 API 响应中提取数据列表。
     * @param response mapserver API 响应
     * @return 非空数据列表；无数据时返回空列表
     */
    private static <T> List<T> data(ApiResponse<List<T>> response) {
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /**
     * 机场基本信息。
     * @param icao ICAO 代码
     * @param iata IATA 代码
     * @param name 机场名称
     * @param city 所属城市
     * @param attr 机场属性
     * @param longitude 经度
     * @param latitude 纬度
     */
    public record GeoAirport(String icao, String iata, String name, String city, String attr,
                             Double longitude, Double latitude) {}

    /** 行政区节点信息。 */
    public record GeoRegion(String id, String parentId, String countryCode, String regionCode,
                            String name, Integer level, String type) {}

    /** 待反向地理编码的坐标点。 */
    public record GeoPoint(String id, Double longitude, Double latitude) {}

    /** 坐标点的反向地理编码结果及其行政区路径。 */
    public record GeoLocation(String id, Double longitude, Double latitude, List<GeoRegion> path,
                              String dataVersion) {}

    /** 行政区祖先路径查询结果。 */
    public record GeoPath(List<GeoRegion> path, String dataVersion) {}

    /** mapserver 的通用 API 响应结构。 */
    private record ApiResponse<T>(String msg, T data) {}
}
