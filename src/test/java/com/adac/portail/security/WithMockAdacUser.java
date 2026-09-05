package com.adac.portail.security;

import com.adac.portail.entity.enums.Role;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TICKET-019 branch-wide review: {@code @WithMockUser} populates the {@code SecurityContext} with
 * Spring Security's own {@code User} principal, not {@link AdacUserDetails} — so
 * {@code @AuthenticationPrincipal AdacUserDetails principal} resolves to {@code null} under it
 * (wrong-type, {@code errorOnInvalidType} defaults to false), and every controller test that
 * matched the service call with {@code any()} passed regardless of whether the real principal
 * ever reached the service. Use this instead wherever a controller method reads
 * {@code @AuthenticationPrincipal AdacUserDetails} — it builds a real {@link AdacUserDetails}
 * wrapping a real {@link com.adac.portail.entity.User}, so tests can assert on it with {@code eq(...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockAdacUserSecurityContextFactory.class)
public @interface WithMockAdacUser {

    long id() default 1L;

    Role role() default Role.SUPER_ADMIN;

    String email() default "caller@adac.fr";

    boolean active() default true;
}
