package com.adac.portail.entity;

import com.adac.portail.entity.enums.TokenType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "activation_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Hash of the 6-digit code (never the code itself — a 6-digit code is only ~20 bits of
     * entropy, so storing it in cleartext would make any DB read of this table directly usable
     * for account takeover). Hashing happens in the auth service (TICKET-015); this column just
     * reserves the right shape for it.
     */
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    /**
     * Failed verification attempts against this token. The auth service (TICKET-015) should
     * invalidate the token past a small threshold, so guessing the code isn't just rate-limited
     * by creation (see docs/DB_MODEL.md) but also bounded per-token.
     */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType type;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Null = still valid; set = already used (invalid). */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
