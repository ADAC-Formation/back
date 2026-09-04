package com.adac.portail.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force throttle for {@code POST /api/auth/login} (TICKET-045) — a
 * {@link ConcurrentHashMap} keyed by {@code email|ip}, no new dependency per the ticket's own
 * scope. Locks a key out for 15 minutes after 5 failures; a success clears it early.
 *
 * <p>In-memory means this resets on every app restart/redeploy and isn't shared across
 * instances — acceptable for the project's current single-instance deployment (see
 * docs/INFRASTRUCTURE.md); revisit if that changes.</p>
 */
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

    /** Package-private: lets {@code LoginAttemptServiceTest} advance time without a real sleep. */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** {@code email} and {@code ip} combined into one lookup key — same shape used everywhere else. */
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

    /**
     * Sliding, not fixed: {@code windowStart} is stamped to <em>this</em> failure, not preserved
     * from the first one in the streak — a lock must expire 15 minutes after the most recent
     * failure, matching the "Réessayez dans 15 minutes" message, not 15 minutes after whichever
     * failure happened to start the count (see TICKET-045 review; a fixed window let a lock lift
     * within seconds of tripping if the 5th failure landed near the end of the old window).
     */
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

    /**
     * Periodic sweep of expired entries. Without it, an attacker who never repeats a key (a
     * unique fake email per request, say) leaves an entry that {@link #isLocked} only ever
     * reclaims when that *exact* key is looked up again — which never happens — growing the map
     * without bound (see TICKET-045 review). {@code isLocked}'s lazy removal stays too: it's what
     * makes a lock "expire on its own" the instant it's checked, without waiting on this sweep.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    void sweepExpiredEntries() {
        int before = attemptsByKey.size();
        attemptsByKey.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int removed = before - attemptsByKey.size();
        if (removed > 0) {
            log.debug("Login attempt sweep: removed {} expired entr{}", removed, removed == 1 ? "y" : "ies");
        }
    }

    /** Test-only: clears all state so integration tests don't leak lockouts into each other via
     * this singleton bean's shared map (see TICKET-045 review). Package-private on purpose —
     * nothing in production code should ever reset another key's counters. */
    void reset() {
        attemptsByKey.clear();
    }

    private boolean isExpired(Attempts attempts) {
        return Duration.between(attempts.windowStart(), clock.instant()).compareTo(WINDOW) >= 0;
    }

    private record Attempts(Instant windowStart, int count) {
    }
}
