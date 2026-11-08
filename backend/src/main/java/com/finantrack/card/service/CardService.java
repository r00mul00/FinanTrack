package com.finantrack.card.service;

import com.finantrack.card.dto.CardRequest;
import com.finantrack.card.dto.CardResponse;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardPurchaseRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {

    private final CreditCardRepository cards;
    private final CreditCardPurchaseRepository purchases;
    private final CardLimitCalculator limits;

    public CardService(CreditCardRepository cards, CreditCardPurchaseRepository purchases, CardLimitCalculator limits) {
        this.cards = cards;
        this.purchases = purchases;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(UUID userId) {
        List<CreditCard> all = cards.findAllByUserIdOrderByNicknameAsc(userId);
        Map<UUID, BigDecimal> used = limits.usedByCard(all.stream().map(CreditCard::getId).toList());
        return all.stream()
                .map(card -> toResponse(card, used.getOrDefault(card.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CardResponse get(UUID id, UUID userId) {
        CreditCard card = find(id, userId);
        return toResponse(card, limits.usedByCard(card.getId()));
    }

    @Transactional
    public CardResponse create(CardRequest request, UUID userId) {
        CreditCard card = cards.saveAndFlush(new CreditCard(userId, request.nickname(), request.brand(),
                request.lastFour(), request.creditLimit(), request.closingDay(), request.dueDay()));
        return toResponse(card, BigDecimal.ZERO);
    }

    /** Novos dias de fechamento e vencimento valem só para faturas ainda não criadas. */
    @Transactional
    public CardResponse update(UUID id, CardRequest request, UUID userId) {
        CreditCard card = cards.findForUpdate(id, userId).orElseThrow(this::notFound);
        card.update(request.nickname(), request.brand(), request.lastFour(), request.creditLimit(),
                request.closingDay(), request.dueDay(), request.archived());
        cards.flush();
        return toResponse(card, limits.usedByCard(card.getId()));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        CreditCard card = cards.findForUpdate(id, userId).orElseThrow(this::notFound);
        if (purchases.existsByCardId(id)) {
            throw new ConflictException("Este cartão possui compras. Arquive-o para manter o histórico.");
        }
        cards.delete(card);
    }

    private CreditCard find(UUID id, UUID userId) {
        return cards.findByIdAndUserId(id, userId).orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Cartão não encontrado.");
    }

    /** Com o limite reduzido abaixo do que já está em uso, o disponível exibido é zero. */
    private CardResponse toResponse(CreditCard card, BigDecimal used) {
        return new CardResponse(card.getId(), card.getNickname(), card.getBrand(), card.getLastFour(),
                card.getCreditLimit(), used, card.getCreditLimit().subtract(used).max(BigDecimal.ZERO),
                card.getClosingDay(), card.getDueDay(), card.isArchived());
    }
}
