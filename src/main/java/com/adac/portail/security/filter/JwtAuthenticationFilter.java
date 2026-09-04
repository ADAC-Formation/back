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

        // Checked before AuthenticationManager is ever touched (TICKET-045 AC) — a locked-out
        // key must not spend a password check (timing, DB round trip) at all.
        String key = loginAttemptService.key(loginRequest.getEmail(), request.getRemoteAddr());
        if (loginAttemptService.isLocked(key)) {
            throw new LoginRateLimitException("Trop de tentatives. Réessayez dans 15 minutes.");
        }

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword());
        try {
            return getAuthenticationManager().authenticate(authRequest);
        } catch (InternalAuthenticationServiceException e) {
            // An infrastructure fault (DB down, CustomUserDetailsService blew up) is not a wrong
            // guess — counting it would let a transient outage lock real users out for 15 min on
            // top of the outage itself (see TICKET-045 review).
            throw e;
        } catch (DisabledException e) {
            // Not-yet-activated is not a wrong guess either — the correct-password, wrong-state
            // case shouldn't burn attempts against the same 5-try budget as actual brute-forcing,
            // especially since the 403 body all but tells the user to retry (see review).
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
            // No HttpServletResponse.SC_TOO_MANY_REQUESTS constant exists (429 predates the
            // classic Servlet SC_* set) — HttpStatus is Spring's, used here only for the value.
            status = HttpStatus.TOO_MANY_REQUESTS.value();
            message = failed.getMessage();
        } else if (failed instanceof InternalAuthenticationServiceException) {
            // Distinct from the hand-thrown AuthenticationServiceException cases below: this one
            // wraps whatever CustomUserDetailsService/JPA threw (DB down, etc.), so its message
            // can carry SQL/connection internals — never echo it to an unauthenticated caller,
            // and it's a 500 (our fault), not a 400 (their fault) — see TICKET-045 review.
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
