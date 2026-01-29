package com.example.flightrebooking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flightRebookingOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Flight Rebooking Service API")
                .description("A production-aware backend service for airline disruption recovery")
                .version("1.0.0"));
    }
}
