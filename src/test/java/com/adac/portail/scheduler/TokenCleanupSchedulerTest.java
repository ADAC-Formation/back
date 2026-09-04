package com.adac.portail.scheduler;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.repository.ActivationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private ActivationTokenRepository activationTokenRepository;

    @InjectMocks
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Test
    void deletesExpiredAndUsedTokensFoundAsOfNow() {
        ActivationToken expired = ActivationToken.builder().id(1L).build();
        ActivationToken used = ActivationToken.builder().id(2L).build();
        when(activationTokenRepository.findAllByUsedAtIsNotNullOrExpiresAtBefore(any(OffsetDateTime.class)))
                .thenReturn(List.of(expired, used));

        tokenCleanupScheduler.purgeExpiredAndUsedTokens();

        verify(activationTokenRepository).deleteAll(List.of(expired, used));
    }

    @Test
    void doesNothingWhenNoTokensAreDueForCleanup() {
        when(activationTokenRepository.findAllByUsedAtIsNotNullOrExpiresAtBefore(any(OffsetDateTime.class)))
                .thenReturn(List.of());

        tokenCleanupScheduler.purgeExpiredAndUsedTokens();

        verify(activationTokenRepository).deleteAll(List.of());
    }
}
