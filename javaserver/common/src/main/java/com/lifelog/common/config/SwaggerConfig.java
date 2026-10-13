package com.lifelog.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                .description("LifeLog service API"))
                .components(new Components().addSecuritySchemes("tokenAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("token")))
                .addSecurityItem(new SecurityRequirement().addList("tokenAuth"));
    }
}
