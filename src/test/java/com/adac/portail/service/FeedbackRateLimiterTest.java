package com.adac.portail.service;

import com.adac.portail.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class FeedbackRateLimiterTest {

    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
    private final FeedbackRateLimiter limiter = new FeedbackRateLimiter(clock);

    @Test
    void acceptsTenFeedbacksWithinAnHour() {
        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
            limiter.record(USER);
            clock.advance(Duration.ofMinutes(1));
        }
    }

    @Test
    void refusesTheEleventhFeedbackWithinAnHour() {
        recordTimes(USER, 10);

        assertThatThrownBy(() -> limiter.checkAllowed(USER))
                .isInstanceOf(RateLimitException.class)
                .hasMessage("Trop de retours envoyés. Réessayez dans une heure.");
    }

    @Test
    void slidingWindowFreesASlotOneHourAfterTheOldestFeedback() {
        limiter.record(USER);
        clock.advance(Duration.ofMinutes(30));
        recordTimes(USER, 9);

        clock.advance(Duration.ofMinutes(29));
        assertThatThrownBy(() -> limiter.checkAllowed(USER)).isInstanceOf(RateLimitException.class);

        clock.advance(Duration.ofMinutes(1)); // la première remonte à une heure pile
        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
    }

    @Test
    void usersAreLimitedIndependently() {
        recordTimes(USER, 10);

        assertThatCode(() -> limiter.checkAllowed(OTHER_USER)).doesNotThrowAnyException();
    }

    @Test
    void sweepForgetsUsersWhoseFeedbacksAreAllOlderThanAnHour() {
        limiter.record(USER);
        clock.advance(Duration.ofMinutes(30));
        limiter.record(OTHER_USER);

        clock.advance(Duration.ofMinutes(31));
        limiter.sweepExpiredEntries();

        assertThat(limiter.trackedUserCount()).isEqualTo(1);
    }

    private void recordTimes(long userId, int times) {
        for (int i = 0; i < times; i++) {
            limiter.record(userId);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
