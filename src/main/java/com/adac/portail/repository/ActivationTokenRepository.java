package com.adac.portail.repository;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.TokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    /** Used by TokenCleanupScheduler (cron 3h) to purge expired/used tokens. */
    List<ActivationToken> findAllByUsedAtIsNotNullOrExpiresAtBefore(OffsetDateTime now);

    /**
     * Most recent still-unused token of this type for a user — what TICKET-015's auth service
     * verifies a submitted code against. No lookup by plaintext token exists: {@code code} was
     * renamed {@code codeHash} in the TICKET-003 review, so verification means fetching this
     * candidate and comparing hashes in the service layer, not querying by value.
     *
     * <p>{@code PESSIMISTIC_WRITE}: the sole caller ({@code ActivationServiceImpl
     * .verifyAndConsumeToken}) reads {@code attempts}, then conditionally increments and saves
     * it, inside one transaction — without a row lock, two concurrent guesses against the same
     * token both read the pre-increment value and the 3-guess cap can be bypassed by racing
     * requests instead of guessing right (see TICKET-015 review). Row lock only, never blocks
     * unrelated tokens or users.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivationToken> findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(User user, TokenType type);

    /**
     * How many tokens of this type were *created* for this user since {@code since} — the
     * creation-rate limit (max 3 / 15 min, see docs/DB_MODEL.md), checked by
     * {@code ActivationServiceImpl} before issuing a new one via resend-activation or
     * forgot-password. Independent of {@code ActivationToken.attempts}, which limits guesses
     * against one already-issued token instead.
     */
    long countByUserAndTypeAndCreatedAtAfter(User user, TokenType type, OffsetDateTime since);

    /**
     * Has this user ever completed an activation of this type? {@code ActivationServiceImpl}
     * uses this to tell "never activated yet" apart from "was active, now admin-suspended" —
     * both look identical as {@code User.isActive() == false} alone (see docs/DB_MODEL.md — no
     * separate status column). Only a suspended account can have a used token here.
     */
    boolean existsByUserAndTypeAndUsedAtIsNotNull(User user, TokenType type);
}
