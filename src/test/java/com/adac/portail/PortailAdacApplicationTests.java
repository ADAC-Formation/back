package com.adac.portail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context loads without error on the dev profile.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PortailAdacApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails.
    }
}
