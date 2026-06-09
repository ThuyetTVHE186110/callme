package com.callme.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the bearer-JWT scheme so Swagger UI's "Authorize" button actually works
 * end-to-end — every endpoint but `/api/auth/**` requires the same token (see
 * {@link SecurityConfig}), so one global requirement covers the whole surface.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI callmeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Callme — Đặt người lái xe hộ API")
                        .description("Dịch vụ đặt tài xế lái xe hộ khách hàng về nhà (designated driver booking).")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
