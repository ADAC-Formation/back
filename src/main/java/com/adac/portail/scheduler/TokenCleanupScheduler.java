package com.adac.portail.scheduler;

import com.adac.portail.entity.ActivationToken;
import com.adac.portail.repository.ActivationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final ActivationTokenRepository activationTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredAndUsedTokens() {
        List<ActivationToken> dueForCleanup =
                activationTokenRepository.findAllByUsedAtIsNotNullOrExpiresAtBefore(OffsetDateTime.now());
        activationTokenRepository.deleteAll(dueForCleanup);
        log.info("Token cleanup: removed {} expired/used activation token(s)", dueForCleanup.size());
    }
}
