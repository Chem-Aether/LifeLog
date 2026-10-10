package com.lifelog.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI lifeLogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LifeLog API")
                .version("1.0")
                .description("LifeLog service API"));
    }
}
