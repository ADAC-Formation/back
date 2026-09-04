package com.adac.portail.repository;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.entity.enums.TokenType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class ActivationTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Test
    void findAllByUsedAtIsNotNullOrExpiresAtBeforeReturnsExpiredAndUsedTokens() {
        User user = userRepository.save(User.builder()
                .email("activation-token-repo-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        OffsetDateTime now = OffsetDateTime.now();

        ActivationToken expired = activationTokenRepository.save(ActivationToken.builder()
                .user(user)
                .codeHash("hash-expired")
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.minusMinutes(5))
                .build());

        ActivationToken used = activationTokenRepository.save(ActivationToken.builder()
                .user(user)
                .codeHash("hash-used")
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(25))
                .usedAt(now.minusMinutes(1))
                .build());

        ActivationToken stillValid = activationTokenRepository.save(ActivationToken.builder()
                .user(user)
                .codeHash("hash-valid")
                .type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(25))
                .build());

        List<ActivationToken> result = activationTokenRepository.findAllByUsedAtIsNotNullOrExpiresAtBefore(now);

        assertThat(result).extracting(ActivationToken::getId)
                .containsExactlyInAnyOrder(expired.getId(), used.getId());
        assertThat(result).extracting(ActivationToken::getId)
                .doesNotContain(stillValid.getId());
    }

    @Test
    void countByUserAndTypeAndCreatedAtAfterCountsOnlyRecentTokensOfThatType() {
        User user = userRepository.save(User.builder()
                .email("activation-token-count-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime beforeInsert = now.minusSeconds(1);

        activationTokenRepository.save(ActivationToken.builder()
                .user(user).codeHash("h1").type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(30)).build());
        activationTokenRepository.save(ActivationToken.builder()
                .user(user).codeHash("h2").type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(30)).build());
        // Different type — must not count toward ACCOUNT_ACTIVATION's limit.
        activationTokenRepository.save(ActivationToken.builder()
                .user(user).codeHash("h3").type(TokenType.PASSWORD_RESET)
                .expiresAt(now.plusMinutes(30)).build());

        OffsetDateTime afterInsert = OffsetDateTime.now().plusSeconds(1);

        assertThat(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                user, TokenType.ACCOUNT_ACTIVATION, beforeInsert))
                .isEqualTo(2);
        assertThat(activationTokenRepository.countByUserAndTypeAndCreatedAtAfter(
                user, TokenType.ACCOUNT_ACTIVATION, afterInsert))
                .isZero();
    }

    @Test
    void existsByUserAndTypeAndUsedAtIsNotNullDistinguishesSuspendedFromNeverActivated() {
        User neverActivated = userRepository.save(User.builder()
                .email("never-activated@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .isActive(false)
                .build());
        User suspended = userRepository.save(User.builder()
                .email("suspended@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("John")
                .role(Role.STAGIAIRE)
                .isActive(false)
                .build());

        OffsetDateTime now = OffsetDateTime.now();
        // neverActivated has only an unused token pending — never completed activation.
        activationTokenRepository.save(ActivationToken.builder()
                .user(neverActivated).codeHash("h1").type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(30)).build());
        // suspended completed activation once (usedAt set) before being disabled again.
        activationTokenRepository.save(ActivationToken.builder()
                .user(suspended).codeHash("h2").type(TokenType.ACCOUNT_ACTIVATION)
                .expiresAt(now.plusMinutes(30)).usedAt(now.minusDays(10)).build());

        assertThat(activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(
                neverActivated, TokenType.ACCOUNT_ACTIVATION)).isFalse();
        assertThat(activationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull(
                suspended, TokenType.ACCOUNT_ACTIVATION)).isTrue();
    }
}
