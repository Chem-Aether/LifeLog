package com.lifelog.travel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.lifelog")
@MapperScan({"com.lifelog.travel.flight", "com.lifelog.travel.footprint", "com.lifelog.travel.train"})
public class TravelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelServiceApplication.class, args);
    }
}
