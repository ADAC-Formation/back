package com.adac.portail.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-045 — no Spring context: {@link LoginAttemptService} is a plain in-memory counter, and
 * the clock is injected (see {@link MutableClock}) so the 15-min-window test doesn't need a real
 * {@code Thread.sleep}.
 */
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
        // Recording all 5 failures at the same instant (as the test above does) can't catch a
        // fixed-window bug — windowStart happens to equal the last failure's time either way.
        // Spreading them out is what actually exercises "sliding": a naive fixed window pinned to
        // the *first* failure would expire here at t+15m (1 minute after this point), not t+29m.
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int i = 0; i < 4; i++) {
            service.recordFailure(KEY);
            clock.advance(Duration.ofMinutes(3));
        }
        service.recordFailure(KEY); // 5th failure, at t+12m
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(14)); // t+26m: 14 min after the 5th failure
        assertThat(service.isLocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(2)); // t+28m: 16 min after the 5th failure
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

        // Only 4 more failures in the new window — must stay unlocked (a naive impl that just
        // keeps accumulating the old count would already be at 5+4=9 and wrongly lock here).
        for (int i = 0; i < 4; i++) {
            service.recordFailure(KEY);
        }
        assertThat(service.isLocked(KEY)).isFalse();
    }

    /** Simple advanceable {@link Clock} test double — TICKET-045 explicitly asks for an injected/
     * mocked clock instead of a real 15-minute sleep. */
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
