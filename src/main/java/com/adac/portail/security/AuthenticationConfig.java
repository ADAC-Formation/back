package com.adac.portail.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes the {@link AuthenticationManager} used by {@code JwtAuthenticationFilter}, backed by
 * Spring's own {@link DaoAuthenticationProvider} rather than a hand-rolled implementation.
 *
 * <p>A hand-rolled manager was tried first and dropped after review: {@code DaoAuthenticationProvider}
 * already gives us, for free, the things that matter for a login endpoint — (1) a dummy
 * password-encoder call when the user isn't found, so an unknown email doesn't return faster than
 * a known one (timing-based user enumeration), and (2) {@code UsernameNotFoundException} is
 * converted to {@code BadCredentialsException} by default so the login endpoint never leaks which
 * emails exist.</p>
 *
 * <p><b>Account-status checks run after the password check, not before</b> — the opposite of
 * {@code DaoAuthenticationProvider}'s own default. Its default {@code preAuthenticationChecks}
 * (locked/expired/<b>enabled</b>) run before the password is ever compared, which turns "account
 * not yet activated" into a free, unthrottled probe: {@code POST /api/auth/login} with a garbage
 * password on a real-but-inactive email returns {@code 403} instead of {@code 401} — no password
 * needed to learn an email is a pending (or admin-suspended) account, and
 * {@code JwtAuthenticationFilter} doesn't count {@code DisabledException} against
 * {@code LoginAttemptService}'s lockout (see its Javadoc — that exemption is correct only once
 * this reordering makes {@code DisabledException} require a correct password first). See
 * TICKET-045 review — found only once TICKET-014/015/045 were combined; splitting the checks like
 * this is exactly what {@code DaoAuthenticationProvider} exposes {@code setPreAuthenticationChecks}/
 * {@code setPostAuthenticationChecks} for.</p>
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

        // Only checks that don't themselves leak information a wrong password would already
        // hide stay pre-auth — none of AdacUserDetails' isAccountNonLocked()/isAccountNonExpired()
        // are ever false today (both hardcoded true), but wiring them correctly here means a
        // future real lockout/expiry feature inherits the same "prove the password first"
        // property automatically instead of silently becoming a new oracle.
        provider.setPreAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Account is locked");
            }
            if (!user.isAccountNonExpired()) {
                throw new AccountExpiredException("Account has expired");
            }
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isCredentialsNonExpired()) {
                throw new CredentialsExpiredException("Credentials have expired");
            }
            if (!user.isEnabled()) {
                throw new DisabledException("Account is disabled");
            }
        });

        return new ProviderManager(provider);
    }
}
