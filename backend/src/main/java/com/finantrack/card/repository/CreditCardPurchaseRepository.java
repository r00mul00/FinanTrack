package com.finantrack.card.repository;

import com.finantrack.card.entity.CreditCardPurchase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditCardPurchaseRepository extends JpaRepository<CreditCardPurchase, UUID> {

    Page<CreditCardPurchase> findAllByCardId(UUID cardId, Pageable pageable);

    Optional<CreditCardPurchase> findByIdAndCardId(UUID id, UUID cardId);

    boolean existsByCardId(UUID cardId);

    long countByCategoryId(UUID categoryId);

    @Modifying
    @Query("update CreditCardPurchase p set p.categoryId = :to, p.updatedAt = :now where p.categoryId = :from")
    int reassignCategory(@Param("from") UUID from, @Param("to") UUID to, @Param("now") Instant now);
}
