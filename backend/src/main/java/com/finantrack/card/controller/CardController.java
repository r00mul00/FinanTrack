package com.finantrack.card.controller;

import com.finantrack.card.dto.CardRequest;
import com.finantrack.card.dto.CardResponse;
import com.finantrack.card.dto.InvoiceDetailResponse;
import com.finantrack.card.dto.InvoiceResponse;
import com.finantrack.card.dto.PaymentRequest;
import com.finantrack.card.dto.PurchaseRequest;
import com.finantrack.card.dto.PurchaseResponse;
import com.finantrack.card.service.CardService;
import com.finantrack.card.service.InvoiceService;
import com.finantrack.card.service.PurchaseService;
import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cartões")
public class CardController {

    private final CardService cards;
    private final PurchaseService purchases;
    private final InvoiceService invoices;
    private final CurrentUser currentUser;

    public CardController(CardService cards, PurchaseService purchases, InvoiceService invoices,
            CurrentUser currentUser) {
        this.cards = cards;
        this.purchases = purchases;
        this.invoices = invoices;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CardResponse> list() {
        return cards.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public CardResponse get(@PathVariable UUID id) {
        return cards.get(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@Valid @RequestBody CardRequest request) {
        return cards.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public CardResponse update(@PathVariable UUID id, @Valid @RequestBody CardRequest request) {
        return cards.update(id, request, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        cards.delete(id, currentUser.id());
    }

    @GetMapping("/{id}/purchases")
    public PageResponse<PurchaseResponse> purchases(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchases.list(id, currentUser.id(), page, size);
    }

    @PostMapping("/{id}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse createPurchase(@PathVariable UUID id, @Valid @RequestBody PurchaseRequest request) {
        return purchases.create(id, request, currentUser.id());
    }

    @DeleteMapping("/{id}/purchases/{purchaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelPurchase(@PathVariable UUID id, @PathVariable UUID purchaseId) {
        purchases.cancel(id, purchaseId, currentUser.id());
    }

    @GetMapping("/{id}/invoices")
    public List<InvoiceResponse> invoices(@PathVariable UUID id) {
        return invoices.list(id, currentUser.id());
    }

    @GetMapping("/{id}/invoices/{invoiceId}")
    public InvoiceDetailResponse invoice(@PathVariable UUID id, @PathVariable UUID invoiceId) {
        return invoices.detail(id, invoiceId, currentUser.id());
    }

    @PostMapping("/{id}/invoices/{invoiceId}/payments")
    public InvoiceResponse pay(@PathVariable UUID id, @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request) {
        return invoices.pay(id, invoiceId, request, currentUser.id());
    }
}
