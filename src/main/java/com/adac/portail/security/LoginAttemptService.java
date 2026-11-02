package com.adac.portail.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public String key(String email, String ip) {
        return email + "|" + ip;
    }

    public boolean isLocked(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return false;
        }
        if (isExpired(attempts)) {
            attemptsByKey.remove(key, attempts);
            return false;
        }
        return attempts.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attemptsByKey.compute(key, (k, existing) -> {
            int count = (existing == null || isExpired(existing)) ? 1 : existing.count() + 1;
            return new Attempts(now, count);
        });
    }

    public void recordSuccess(String key) {
        attemptsByKey.remove(key);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    void sweepExpiredEntries() {
        int before = attemptsByKey.size();
        attemptsByKey.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int removed = before - attemptsByKey.size();
        if (removed > 0) {
            log.debug("Login attempt sweep: removed {} expired entr{}", removed, removed == 1 ? "y" : "ies");
        }
    }

    void reset() {
        attemptsByKey.clear();
    }

    private boolean isExpired(Attempts attempts) {
        return Duration.between(attempts.windowStart(), clock.instant()).compareTo(WINDOW) >= 0;
    }

    private record Attempts(Instant windowStart, int count) {
    }
}
