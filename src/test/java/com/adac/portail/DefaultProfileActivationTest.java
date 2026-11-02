package com.adac.portail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultProfileActivationTest {

    @Autowired
    private Environment environment;

    @Test
    void defaultsToDevProfileWhenNoneIsExplicitlySet() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
    }
}
