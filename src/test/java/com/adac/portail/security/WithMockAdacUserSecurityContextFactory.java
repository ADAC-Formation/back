package com.adac.portail.security;

import com.adac.portail.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/** See {@link WithMockAdacUser}'s Javadoc. */
public class WithMockAdacUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAdacUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAdacUser annotation) {
        User user = User.builder()
                .id(annotation.id())
                .email(annotation.email())
                .nom("Test")
                .prenom("User")
                .role(annotation.role())
                .isActive(annotation.active())
                .build();
        AdacUserDetails principal = new AdacUserDetails(user);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return context;
    }
}
