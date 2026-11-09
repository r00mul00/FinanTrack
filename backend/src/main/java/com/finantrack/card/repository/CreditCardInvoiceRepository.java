package com.finantrack.card.repository;

import com.finantrack.card.entity.CreditCardInvoice;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardInvoiceRepository extends JpaRepository<CreditCardInvoice, UUID> {

    Optional<CreditCardInvoice> findByCardIdAndReferenceMonth(UUID cardId, LocalDate referenceMonth);

    Optional<CreditCardInvoice> findByIdAndCardId(UUID id, UUID cardId);

    List<CreditCardInvoice> findAllByCardIdOrderByReferenceMonthDesc(UUID cardId);

    List<CreditCardInvoice> findAllByCardIdIn(Collection<UUID> cardIds);

    List<CreditCardInvoice> findAllByDueDateBetween(LocalDate from, LocalDate to);

    List<CreditCardInvoice> findAllByCardIdInAndDueDateLessThanEqual(Collection<UUID> cardIds, LocalDate limit);
}
