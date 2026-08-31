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
}
