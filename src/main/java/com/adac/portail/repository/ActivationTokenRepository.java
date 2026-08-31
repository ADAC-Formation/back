package com.adac.portail.repository;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

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
     */
    Optional<ActivationToken> findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(User user, TokenType type);
}
