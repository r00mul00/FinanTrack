package com.finantrack.recurring.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.recurring.entity.RecurringStatus;
import com.finantrack.recurring.entity.RecurringTransaction;
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.transaction.service.TransactionSnapshot;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gera os lançamentos devidos de uma recorrência, em uma transação por recorrência. */
@Component
public class RecurringOccurrenceWriter {

    private final RecurringTransactionRepository recurring;
    private final TransactionRepository transactions;
    private final AuditRecorder audit;

    public RecurringOccurrenceWriter(RecurringTransactionRepository recurring, TransactionRepository transactions,
            AuditRecorder audit) {
        this.recurring = recurring;
        this.transactions = transactions;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateDue(UUID id, LocalDate today) {
        recurring.findForUpdate(id).ifPresent(item -> catchUp(item, today));
    }

    /** Chamado com a recorrência já em uma transação, ao criar ou reativar. */
    public void catchUp(RecurringTransaction item, LocalDate today) {
        if (item.getStatus() != RecurringStatus.ACTIVE) {
            return;
        }
        LocalDate next = item.getNextOccurrenceDate();
        while (next != null && !next.isAfter(today) && withinPeriod(item, next)) {
            if (!transactions.existsOccurrence(item.getId(), next)) {
                Transaction occurrence = transactions.save(
                        Transaction.recurringOccurrence(item.getUserId(), item.getId(), next, details(item, next)));
                audit.record(item.getUserId(), AuditAction.CREATE, AuditEntityType.TRANSACTION, occurrence.getId(), null,
                        TransactionSnapshot.of(occurrence));
            }
            next = RecurrenceCalendar.firstAfter(item.getStartDate(), item.getFrequency(), next);
        }
        applyCursor(item, next);
        transactions.flush();
    }

    /** Sem próximo ciclo dentro do período, a recorrência se encerra. */
    public void applyCursor(RecurringTransaction item, LocalDate next) {
        if (next == null || !withinPeriod(item, next)) {
            item.finish();
        } else {
            item.moveCursorTo(next);
        }
    }

    private boolean withinPeriod(RecurringTransaction item, LocalDate date) {
        return item.getEndDate() == null || !date.isAfter(item.getEndDate());
    }

    private Transaction.Details details(RecurringTransaction item, LocalDate date) {
        return new Transaction.Details(item.getDescription(), item.getAmount(), item.getType(), TransactionStatus.PENDING,
                item.getCategoryId(), item.getAccountId(), date, date, null);
    }
}
