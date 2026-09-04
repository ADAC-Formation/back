package com.adac.portail.security.filter;

import com.adac.portail.dto.request.LoginRequest;
import com.adac.portail.dto.response.ErrorResponse;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.security.AdacUserDetails;
import com.adac.portail.security.JwtCookieFactory;
import com.adac.portail.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles {@code POST /api/auth/login} directly (no {@code AuthController} — see ARCHI.md).
 * Parses a JSON body instead of the default form-encoded one, poses the {@code jwt} HttpOnly
 * cookie on success (see CLAUDE.md — Auth section for the exact cookie shape) and returns the
 * {@code UserResponse} body docs/tech.md's {@code POST /api/auth/login} contract requires.
 */
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final UserMapper userMapper;
    private final JwtCookieFactory jwtCookieFactory;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager,
                                    JwtTokenService jwtTokenService,
                                    ObjectMapper objectMapper,
                                    Validator validator,
                                    UserMapper userMapper,
                                    JwtCookieFactory jwtCookieFactory) {
        super(authenticationManager);
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.userMapper = userMapper;
        this.jwtCookieFactory = jwtCookieFactory;
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

        // Bean validation isn't wired for filters the way it is for @RequestBody controller
        // args — attemptAuthentication runs outside Spring MVC's argument resolution, so it has
        // to be triggered by hand here.
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new AuthenticationServiceException("Corps de requête invalide : " + details);
        }

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword());
        return getAuthenticationManager().authenticate(authRequest);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain, Authentication authResult) throws IOException {
        AdacUserDetails principal = (AdacUserDetails) authResult.getPrincipal();
        String token = jwtTokenService.generateToken(principal);

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
        if (failed instanceof DisabledException) {
            status = HttpServletResponse.SC_FORBIDDEN;
            message = "Compte non activé. Veuillez consulter vos emails.";
        } else if (failed instanceof AuthenticationServiceException) {
            status = HttpServletResponse.SC_BAD_REQUEST;
            message = failed.getMessage();
        } else {
            status = HttpServletResponse.SC_UNAUTHORIZED;
            message = "Identifiants invalides";
        }

        log.warn("Login failed for request from {}: {}", request.getRemoteAddr(), failed.getClass().getSimpleName());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(status, message, List.of()));
    }
}
