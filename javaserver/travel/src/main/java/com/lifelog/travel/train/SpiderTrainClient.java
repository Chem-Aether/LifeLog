package com.lifelog.travel.train;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 调用外部车次时刻服务查询列车经停站。 */
@Component
public class SpiderTrainClient {
    private final RestClient client;

    /**
     * 创建车次服务客户端。
     * @param baseUrl 外部车次服务基础地址
     */
    public SpiderTrainClient(@Value("${spiderserver.base-url:http://127.0.0.1:8082}") String baseUrl) {
        client = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 查询指定日期车次的途经站时刻信息。
     * @param trainNo 车次号
     * @param date 查询日期
     * @return 车次时刻数据
     */
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
