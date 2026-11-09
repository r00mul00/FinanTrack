package com.finantrack.card.repository;

import com.finantrack.card.entity.CreditCard;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findAllByUserIdOrderByNicknameAsc(UUID userId);

    List<CreditCard> findTop5ByUserIdAndNicknameContainingIgnoreCaseOrderByNicknameAsc(UUID userId, String term);

    Optional<CreditCard> findByIdAndUserId(UUID id, UUID userId);

    /** Compra, cancelamento e pagamento passam por este lock: o limite nunca é lido desatualizado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditCard c where c.id = :id and c.userId = :userId")
    Optional<CreditCard> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
