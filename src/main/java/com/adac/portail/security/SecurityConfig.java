package com.adac.portail.security;

import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.security.filter.JwtAuthenticationFilter;
import com.adac.portail.security.filter.JwtAuthorizationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security config: stateless JWT-in-HttpOnly-cookie auth (see CLAUDE.md
 * — Auth section).
 *
 * <p>
 * CSRF stays disabled — {@code SameSite=Strict} on the {@code jwt} cookie
 * already stops it
 * being sent on cross-site requests, which neutralizes the classic CSRF vector
 * (see ARCHI.md —
 * Authentification for the full reasoning and its accepted residual risk).
 *
 * <p>{@code @EnableMethodSecurity} (branch-wide review, TICKET-047): without it, Spring never
 * builds the AOP advisor behind {@code @PreAuthorize}, so every such annotation is silently
 * ignored — the URL rules below (just {@code .anyRequest().authenticated()} for anything not in
 * {@code PUBLIC_ROUTES}) are all that would actually run. {@code CategoryController} is the first
 * {@code @PreAuthorize} consumer on this branch, which is what surfaced the gap: any authenticated
 * role, not just SUPER_ADMIN, could otherwise create/rename/activate/deactivate categories. The
 * equivalent annotation already exists on {@code dev} (added alongside {@code UserController} in
 * TICKET-019) — this branch simply predates that commit.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        // TICKET-045: explicit list, NOT "/api/auth/**" — that wildcard made every future
        // endpoint under /api/auth/ public by default with zero code signal (exactly what bit
        // GET /api/auth/me before this ticket — see AuthController's Javadoc). Deliberately
        // excludes /api/auth/me (must stay authenticated) and /api/auth/logout (must too — a
        // public logout is a cheap forced-logout vector for any cross-site page, see TICKET-014
        // review).
        private static final String[] PUBLIC_ROUTES = {
                        "/api/auth/login", "/api/auth/activate", "/api/auth/resend-activation",
                        "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health"
        };

        private final AuthenticationManager authenticationManager;
        private final JwtTokenService jwtTokenService;
        private final JwtAuthorizationFilter jwtAuthorizationFilter;
        private final ObjectMapper objectMapper;
        private final Validator validator;
        private final UserMapper userMapper;
        private final JwtCookieFactory jwtCookieFactory;
        private final LoginAttemptService loginAttemptService;

        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                                authenticationManager, jwtTokenService, objectMapper, validator, userMapper,
                                jwtCookieFactory, loginAttemptService);

                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(PUBLIC_ROUTES).permitAll()
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response,
                                                                authException) -> writeJsonError(response,
                                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "Authentification requise"))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> writeJsonError(response,
                                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                                "Droits insuffisants")))
                                .httpBasic(basic -> basic.disable())
                                .formLogin(form -> form.disable())
                                .addFilterAt(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        private void writeJsonError(HttpServletResponse response, int status, String message)
                        throws java.io.IOException {
                response.setStatus(status);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), new ErrorResponse(status, message));
        }

        // JwtAuthorizationFilter is also a @Component so Spring Boot would otherwise
        // auto-register
        // it a second time as a plain servlet filter (outside the security chain) via
        // ServletContextInitializerBeans — harmless today since it's idempotent, but
        // disabled
        // explicitly so its only execution is the addFilterBefore position above.
        @Bean
        public FilterRegistrationBean<JwtAuthorizationFilter> disableAutoRegistration(JwtAuthorizationFilter filter) {
                FilterRegistrationBean<JwtAuthorizationFilter> registration = new FilterRegistrationBean<>(filter);
                registration.setEnabled(false);
                return registration;
        }

        private CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .toList());
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
