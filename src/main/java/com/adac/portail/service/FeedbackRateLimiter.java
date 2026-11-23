package com.adac.portail.service;

import com.adac.portail.exception.RateLimitException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les retours à 10 par utilisateur sur une heure glissante. Compteur en mémoire, comme
 * {@code LoginAttemptService} : remis à zéro au redémarrage, suffisant pour une seule instance du
 * back (il protège la boîte du développeur et le quota d'emails d'une boucle, pas d'un attaquant).
 */
@Component
public class FeedbackRateLimiter {

    static final int MAX_FEEDBACKS = 10;
    static final Duration WINDOW = Duration.ofHours(1);
    private static final String LIMIT_MESSAGE = "Trop de retours envoyés. Réessayez dans une heure.";

    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Instant>> sentAtByUser = new ConcurrentHashMap<>();

    public FeedbackRateLimiter() {
        this(Clock.systemUTC());
    }

    FeedbackRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @throws RateLimitException si l'utilisateur a déjà envoyé 10 retours dans l'heure */
    public void checkAllowed(Long userId) {
        Deque<Instant> sentAt = sentAtByUser.get(userId);
        if (sentAt == null) {
            return;
        }
        synchronized (sentAt) {
            dropExpired(sentAt);
            if (sentAt.size() >= MAX_FEEDBACKS) {
                throw new RateLimitException(LIMIT_MESSAGE);
            }
        }
    }

    /** Enregistre un retour effectivement envoyé (un échec d'envoi ne consomme pas le quota). */
    public void record(Long userId) {
        Deque<Instant> sentAt = sentAtByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (sentAt) {
            dropExpired(sentAt);
            sentAt.addLast(clock.instant());
        }
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    void sweepExpiredEntries() {
        sentAtByUser.entrySet().removeIf(entry -> {
            Deque<Instant> sentAt = entry.getValue();
            synchronized (sentAt) {
                dropExpired(sentAt);
                return sentAt.isEmpty();
            }
        });
    }

    int trackedUserCount() {
        return sentAtByUser.size();
    }

    private void dropExpired(Deque<Instant> sentAt) {
        Instant windowStart = clock.instant().minus(WINDOW);
        while (!sentAt.isEmpty() && !sentAt.peekFirst().isAfter(windowStart)) {
            sentAt.pollFirst();
        }
    }
}
