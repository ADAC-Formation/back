package com.adac.portail.security;

import com.adac.portail.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps the domain {@link User} instead of Spring Security's built-in {@code User} principal, so
 * anything reading {@code Authentication.getPrincipal()} — the login response, a future
 * {@code @AuthenticationPrincipal} in a controller — has the full entity (id, nom, prenom,
 * emailNotificationsEnabled, ...) without a second DB round trip.
 */
public class AdacUserDetails implements UserDetails {

    private final User user;

    public AdacUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
