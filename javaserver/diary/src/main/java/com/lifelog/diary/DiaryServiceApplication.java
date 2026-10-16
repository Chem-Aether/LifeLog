package com.lifelog.diary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 日记服务的 Spring Boot 启动入口。 */
@SpringBootApplication(scanBasePackages = {"com.lifelog.diary", "com.lifelog.common"})
public class DiaryServiceApplication {
    /**
     * 启动日记服务。
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(DiaryServiceApplication.class, args); }
}
