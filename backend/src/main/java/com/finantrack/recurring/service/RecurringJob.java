package com.finantrack.recurring.service;

import com.finantrack.recurring.repository.RecurringTransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringJob.class);

    private final RecurringTransactionRepository recurring;
    private final RecurringOccurrenceWriter writer;
    private final Clock clock;

    public RecurringJob(RecurringTransactionRepository recurring, RecurringOccurrenceWriter writer, Clock clock) {
        this.recurring = recurring;
        this.writer = writer;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "America/Sao_Paulo")
    public void runDaily() {
        run(LocalDate.now(clock));
    }

    /** Recupera ciclos perdidos enquanto a aplicação esteve desligada. */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run(LocalDate.now(clock));
    }

    /** Uma recorrência com problema não impede o processamento das demais. */
    public void run(LocalDate today) {
        for (UUID id : recurring.findDueIds(today)) {
            try {
                writer.generateDue(id, today);
            } catch (RuntimeException ex) {
                log.error("Falha ao gerar lançamentos da recorrência {}", id, ex);
            }
        }
    }
}
