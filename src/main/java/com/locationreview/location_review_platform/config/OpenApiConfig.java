package com.locationreview.location_review_platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI locationReviewApi() {
        return new OpenAPI()
                .info(new Info().title("Worth a Stop API").version("v1")
                        .description("Session based API. Browser mutations require a CSRF token."))
                .schemaRequirement("sessionCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID"));
    }
}
