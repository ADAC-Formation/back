package com.adac.portail.repository;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.TokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    @Query("""
            select t from ActivationToken t
            where (t.usedAt is null and t.expiresAt < :now)
               or (t.usedAt is not null and t.type <> com.adac.portail.entity.enums.TokenType.ACCOUNT_ACTIVATION)
            """)
    List<ActivationToken> findAllDueForCleanup(@Param("now") OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivationToken> findFirstByUserAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(User user, TokenType type);

    long countByUserAndTypeAndCreatedAtAfter(User user, TokenType type, OffsetDateTime since);

    boolean existsByUserAndTypeAndUsedAtIsNotNull(User user, TokenType type);
}
