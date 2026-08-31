package com.adac.portail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real local-dev incident (2026-08-31): every other test in this suite
 * forces @ActiveProfiles("dev"), which hid the fact that a bare `mvn spring-boot:run` (no flag,
 * no OS env var) never activated any profile at all — SPRING_PROFILES_ACTIVE in `.env` is loaded
 * as a plain .properties key ("SPRING_PROFILES_ACTIVE"), which Spring does NOT auto-translate to
 * "spring.profiles.active" the way it does for a real OS environment variable. No active profile
 * → no datasource → app fails to start, silently, from the developer's point of view (port 8080
 * just never opens).
 *
 * This test intentionally has NO @ActiveProfiles — it exercises exactly the bare
 * `mvn spring-boot:run` path, so a regression here means Charlotte is locked out of local dev again.
 */
@SpringBootTest
class DefaultProfileActivationTest {

    @Autowired
    private Environment environment;

    @Test
    void defaultsToDevProfileWhenNoneIsExplicitlySet() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
    }
}
