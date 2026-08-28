package com.adac.portail.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Branded OpenAPI doc (springdoc's default title/description are generic). Also documents the
 * {@code jwt} HttpOnly cookie auth (see CLAUDE.md — Auth section) as an API-key-in-cookie
 * security scheme, since there's no Authorization header to describe.
 *
 * <p>The scheme is applied globally via {@code addSecurityItem} so every operation shows the
 * Swagger UI "Authorize" affordance by default. The handful of actually-public routes
 * (currently just {@code POST /api/auth/login} — see {@code SecurityConfig.PUBLIC_ROUTES}) will
 * need {@code @SecurityRequirements} (empty) once their controller exists, to opt back out.</p>
 */
@Configuration
public class SwaggerConfig {

    private static final String JWT_COOKIE_SCHEME = "jwtCookieAuth";

    @Bean
    public OpenAPI adacOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Portail de Formation ADAC")
                        .description("API du portail de gestion des formations ADAC — auth via cookie HttpOnly `jwt`.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(JWT_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_COOKIE_SCHEME));
    }
}
