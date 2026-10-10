package com.lifelog.travel.train;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class SpiderTrainClient {
    private final RestClient client;

    public SpiderTrainClient(@Value("${spiderserver.base-url:http://127.0.0.1:8082}") String baseUrl) {
        client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> stations(String trainNo, LocalDate date) {
        Map<String, Object> response = client.get().uri(builder -> builder.path("/stations")
                        .queryParam("train_code", trainNo)
                        .queryParam("date", date.format(DateTimeFormatter.BASIC_ISO_DATE)).build())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        Object data = response == null ? null : response.get("data");
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
