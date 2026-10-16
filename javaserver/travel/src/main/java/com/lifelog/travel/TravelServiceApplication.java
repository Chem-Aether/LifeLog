package com.lifelog.travel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 旅行服务的 Spring Boot 启动入口。 */
@SpringBootApplication(scanBasePackages = "com.lifelog")
@MapperScan({"com.lifelog.travel.flight", "com.lifelog.travel.footprint", "com.lifelog.travel.train"})
public class TravelServiceApplication {
    /**
     * 启动旅行服务。
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TravelServiceApplication.class, args);
    }
}
