package com.lifelog.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 身份认证服务的 Spring Boot 启动入口。 */
@SpringBootApplication(scanBasePackages = "com.lifelog")
@MapperScan("com.lifelog.auth.user")
public class AuthServiceApplication {
    /**
     * 启动身份认证服务。
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(AuthServiceApplication.class, args); }
}
