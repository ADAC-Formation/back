package com.adac.portail.security;

import com.adac.portail.entity.enums.Role;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockAdacUserSecurityContextFactory.class)
public @interface WithMockAdacUser {

    long id() default 1L;

    Role role() default Role.SUPER_ADMIN;

    String email() default "caller@adac.fr";

    boolean active() default true;
}
