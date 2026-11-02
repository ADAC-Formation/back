package com.adac.portail.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
