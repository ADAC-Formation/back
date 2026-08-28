package com.adac.portail.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder security configuration.
 *
 * <p>Spring Security is added to the classpath in this ticket (TICKET-001) so that later
 * tickets can build on it, but it has no JWT filter chain yet — that is introduced in
 * TICKET-006. Until then, all requests are permitted so Swagger UI stays reachable and
 * subsequent controller-focused tickets aren't blocked by Spring Security's default
 * auto-generated login.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is disabled because the JWT cookie is SameSite=Strict (see docs/STACK.md) —
                // that alone blocks cross-site requests from carrying it. Do not relax SameSite
                // without re-enabling CSRF protection.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
