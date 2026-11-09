package com.finantrack.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

    private final NotificationGenerator generator;
    private final Clock clock;

    public NotificationJob(NotificationGenerator generator, Clock clock) {
        this.generator = generator;
        this.clock = clock;
    }

    /** Depois do job de recorrências (02:00), para já considerar os lançamentos gerados no dia. */
    @Scheduled(cron = "0 30 2 * * *", zone = "America/Sao_Paulo")
    public void runDaily() {
        run(LocalDate.now(clock));
    }

    /** Recupera avisos perdidos enquanto a aplicação esteve desligada. */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate today) {
        runSafely("orçamentos", generator::generateBudgetAlerts, today);
        runSafely("vencimentos", generator::generateTransactionDueDates, today);
        runSafely("faturas", generator::generateInvoiceDueDates, today);
    }

    /** Uma falha em um tipo de aviso não impede os demais. */
    private void runSafely(String name, Consumer<LocalDate> step, LocalDate today) {
        try {
            step.accept(today);
        } catch (RuntimeException ex) {
            log.error("Falha ao gerar notificações de {}", name, ex);
        }
    }
}
