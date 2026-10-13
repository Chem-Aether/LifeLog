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

@Component
public class GeoReferenceClient {

    private final RestClient restClient;

    public GeoReferenceClient(@Value("${mapserver.base-url:http://127.0.0.1:8765}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

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

    private static <T> List<T> data(ApiResponse<List<T>> response) {
        return response == null || response.data() == null ? List.of() : response.data();
    }

    public record GeoAirport(String icao, String iata, String name, String city, String attr,
                             Double longitude, Double latitude) {}

    public record GeoRegion(String id, String parentId, String countryCode, String regionCode,
                            String name, Integer level, String type) {}

    public record GeoPoint(String id, Double longitude, Double latitude) {}

    public record GeoLocation(String id, Double longitude, Double latitude, List<GeoRegion> path,
                              String dataVersion) {}

    private record ApiResponse<T>(String msg, T data) {}
}
