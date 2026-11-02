package com.adac.portail.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String KEY = "stagiaire@adac.fr|127.0.0.1";

    @Test
    void fiveFailuresLockTheKeyAndASuccessUnlocksIt() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(Instant.now(), ZoneOffset.UTC));

        for (int i = 0; i < 5; i++) {
            service.recordFailure(KEY);
        }
        assertThat(service.isLocked(KEY)).isTrue();

        service.recordSuccess(KEY);
        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void fewerThanFiveFailuresDoesNotLock() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(Instant.now(), ZoneOffset.UTC));

        for (int i = 0; i < 4; i++) {
            service.recordFailure(KEY);
        }

        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void lockExpiresAfterFifteenMinutesWithoutManualUnlock() {
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int i = 0; i < 5; i++) {
            service.recordFailure(KEY);
        }
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(14));
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(2));
        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void windowSlidesWithEachFailure_soTheLockHolds15MinutesAfterTheLastOneNotTheFirst() {

        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int i = 0; i < 4; i++) {
            service.recordFailure(KEY);
            clock.advance(Duration.ofMinutes(3));
        }
        service.recordFailure(KEY);
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(14));
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(2));
        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void windowResetsAfterExpiry_soAFreshFailureStartsCountingFromOne() {
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int i = 0; i < 5; i++) {
            service.recordFailure(KEY);
        }
        clock.advance(Duration.ofMinutes(16));
        assertThat(service.isLocked(KEY)).isFalse();

        for (int i = 0; i < 4; i++) {
            service.recordFailure(KEY);
        }
        assertThat(service.isLocked(KEY)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
