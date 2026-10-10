package com.lifelog.travel.train;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Component
public class RailwayRouteClient {
    private static final Logger log = LoggerFactory.getLogger(RailwayRouteClient.class);
    private final RestClient client;
    private final String baseUrl;

    public RailwayRouteClient(@Value("${mapserver.base-url:http://127.0.0.1:8765}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(60_000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sample(String trainNo, List<String> stations) {
        try {
            Map<String, Object> response = client.post().uri("/api/railway/routes/sample")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("trainCode", trainNo == null ? "" : trainNo, "stations", stations))
                .retrieve().body(new ParameterizedTypeReference<>() {});
            Object data = response == null ? null : response.get("data");
            return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (RestClientException exception) {
            log.error("Railway route sampling request failed: baseUrl={}, trainNo={}, stations={}",
                    baseUrl, trainNo, stations.size(), exception);
            throw exception;
        }
    }
}
