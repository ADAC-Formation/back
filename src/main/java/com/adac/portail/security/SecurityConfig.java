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

@Configuration
@EnableWebSecurity

@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private static final String[] PUBLIC_ROUTES = {
                        "/api/auth/login", "/api/auth/activate", "/api/auth/resend-activation",
                        "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health",

                        "/error"
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
