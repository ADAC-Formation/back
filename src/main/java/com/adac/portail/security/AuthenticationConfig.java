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

@Configuration
@RequiredArgsConstructor
public class AuthenticationConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

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
