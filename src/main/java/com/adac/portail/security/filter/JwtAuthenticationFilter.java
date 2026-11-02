package com.adac.portail.security.filter;

import com.adac.portail.dto.request.LoginRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.security.JwtTokenService;
import com.adac.portail.security.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final UserMapper userMapper;
    private final JwtCookieFactory jwtCookieFactory;
    private final LoginAttemptService loginAttemptService;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager,
                                    JwtTokenService jwtTokenService,
                                    ObjectMapper objectMapper,
                                    Validator validator,
                                    UserMapper userMapper,
                                    JwtCookieFactory jwtCookieFactory,
                                    LoginAttemptService loginAttemptService) {
        super(authenticationManager);
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.userMapper = userMapper;
        this.jwtCookieFactory = jwtCookieFactory;
        this.loginAttemptService = loginAttemptService;
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        LoginRequest loginRequest;
        try {
            loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Corps de requête invalide", e);
        }

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new AuthenticationServiceException("Corps de requête invalide : " + details);
        }

        String key = loginAttemptService.key(loginRequest.getEmail(), request.getRemoteAddr());
        if (loginAttemptService.isLocked(key)) {
            throw new LoginRateLimitException("Trop de tentatives. Réessayez dans 15 minutes.");
        }

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword());
        try {
            return getAuthenticationManager().authenticate(authRequest);
        } catch (InternalAuthenticationServiceException e) {

            throw e;
        } catch (DisabledException e) {

            throw e;
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(key);
            throw e;
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain, Authentication authResult) throws IOException {
        AdacUserDetails principal = (AdacUserDetails) authResult.getPrincipal();
        String token = jwtTokenService.generateToken(principal);

        loginAttemptService.recordSuccess(loginAttemptService.key(principal.getUsername(), request.getRemoteAddr()));

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieFactory.issue(token).toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), userMapper.toResponse(principal.getUser()));

        log.info("Login succeeded for {}", principal.getUsername());
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                               AuthenticationException failed) throws IOException {
        int status;
        String message;
        if (failed instanceof LoginRateLimitException) {

            status = HttpStatus.TOO_MANY_REQUESTS.value();
            message = failed.getMessage();
        } else if (failed instanceof InternalAuthenticationServiceException) {

            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = "Erreur technique";
            log.error("Login failed due to an internal error", failed);
        } else if (failed instanceof DisabledException) {
            status = HttpServletResponse.SC_FORBIDDEN;
            message = "Compte non activé. Veuillez consulter vos emails.";
        } else if (failed instanceof AuthenticationServiceException) {
            status = HttpServletResponse.SC_BAD_REQUEST;
            message = failed.getMessage();
        } else {
            status = HttpServletResponse.SC_UNAUTHORIZED;
            message = "Identifiants invalides";
        }

        if (status != HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.warn("Login failed for request from {}: {}", request.getRemoteAddr(), failed.getClass().getSimpleName());
        }

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(status, message, List.of()));
    }
}
