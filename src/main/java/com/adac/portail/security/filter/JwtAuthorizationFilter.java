package com.adac.portail.security.filter;

import com.adac.portail.security.CustomUserDetailsService;
import com.adac.portail.security.JwtTokenService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Reads the {@code jwt} HttpOnly cookie on every request and, if valid, populates the
 * {@link SecurityContextHolder} — see CLAUDE.md, Auth section, for the exact cookie-reading
 * snippet this follows.
 *
 * <p>A stale token (user deleted, or deactivated since the cookie was issued) must never
 * authenticate the request — {@link UsernameNotFoundException} and account-status checks are
 * both handled here, before the chain continues, rather than left to leak as a 500.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);
    private static final AccountStatusUserDetailsChecker STATUS_CHECKER = new AccountStatusUserDetailsChecker();

    private final JwtTokenService jwtTokenService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            extractTokenFromCookies(request)
                    .flatMap(jwtTokenService::verify)
                    .map(DecodedJWT::getSubject)
                    .map(userDetailsService::loadUserByUsername)
                    .ifPresent(userDetails -> authenticate(userDetails, request));
        } catch (UsernameNotFoundException | AccountStatusException e) {
            // Deleted or deactivated user still holding a live cookie — leave the context empty
            // so the request falls through to the 401 entry point instead of a raw 500.
            log.debug("Rejecting stale jwt cookie: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(UserDetails userDetails, HttpServletRequest request) {
        STATUS_CHECKER.check(userDetails);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private Optional<String> extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
