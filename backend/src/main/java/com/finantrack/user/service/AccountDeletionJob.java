package com.finantrack.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountDeletionJob {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionJob.class);

    private final AccountDeletionService service;
    private final Clock clock;

    public AccountDeletionJob(AccountDeletionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void runDaily() {
        run(clock.instant());
    }

    /** Recupera exclusões perdidas enquanto a aplicação esteve desligada. */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run(clock.instant());
    }

    /** Cada conta em sua transação: uma falha não impede as demais. */
    public void run(Instant now) {
        for (UUID userId : service.findDue(now)) {
            try {
                service.purge(userId, now);
            } catch (RuntimeException ex) {
                log.error("Falha ao excluir a conta {}", userId, ex);
            }
        }
    }
}
