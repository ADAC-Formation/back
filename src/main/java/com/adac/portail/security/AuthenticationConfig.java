package com.adac.portail.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes the {@link AuthenticationManager} used by {@code JwtAuthenticationFilter}, backed by
 * Spring's own {@link DaoAuthenticationProvider} rather than a hand-rolled implementation.
 *
 * <p>A hand-rolled manager was tried first and dropped after review: {@code DaoAuthenticationProvider}
 * already gives us, for free, the three things that matter for a login endpoint —
 * (1) a dummy password-encoder call when the user isn't found, so an unknown email doesn't return
 * faster than a known one (timing-based user enumeration), (2) account-status checks
 * (enabled/locked/expired) run via {@link AdacUserDetails} <em>before</em> the password is even
 * compared, and (3) {@code UsernameNotFoundException} is converted to {@code BadCredentialsException}
 * by default so the login endpoint never leaks which emails exist.</p>
 */
@Configuration
@RequiredArgsConstructor
public class AuthenticationConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
