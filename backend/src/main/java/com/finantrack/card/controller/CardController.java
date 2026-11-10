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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "Lista os cartões", description = "Com o limite em uso: o que falta pagar em todas as faturas, inclusive as parcelas futuras.")
    @GetMapping
    public List<CardResponse> list() {
        return cards.list(currentUser.id());
    }

    @Operation(summary = "Detalha um cartão")
    @GetMapping("/{id}")
    public CardResponse get(@PathVariable UUID id) {
        return cards.get(id, currentUser.id());
    }

    @Operation(summary = "Cria um cartão")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@Valid @RequestBody CardRequest request) {
        return cards.create(request, currentUser.id());
    }

    @Operation(summary = "Altera um cartão")
    @PutMapping("/{id}")
    public CardResponse update(@PathVariable UUID id, @Valid @RequestBody CardRequest request) {
        return cards.update(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui um cartão", description = "Só é possível sem compras; caso contrário, arquive.")
    @ApiResponse(responseCode = "409", description = "O cartão tem compras.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        cards.delete(id, currentUser.id());
    }

    @Operation(summary = "Lista as compras do cartão", description = "Paginada, da mais recente para a mais antiga.")
    @GetMapping("/{id}/purchases")
    public PageResponse<PurchaseResponse> purchases(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchases.list(id, currentUser.id(), page, size);
    }

    @Operation(summary = "Registra uma compra", description = "Divide o valor em parcelas exatas (a diferença de centavos vai para a primeira) e distribui cada uma na fatura do mês, conforme o dia de fechamento. Cria as faturas que faltarem.")
    @ApiResponse(responseCode = "422", description = "Limite insuficiente, cartão arquivado, categoria inválida ou valor pequeno demais para as parcelas.")
    @PostMapping("/{id}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse createPurchase(@PathVariable UUID id, @Valid @RequestBody PurchaseRequest request) {
        return purchases.create(id, request, currentUser.id());
    }

    @Operation(summary = "Cancela uma compra", description = "As parcelas saem das faturas e dos totais.")
    @ApiResponse(responseCode = "422", description = "Alguma parcela está em fatura com pagamento registrado.")
    @DeleteMapping("/{id}/purchases/{purchaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelPurchase(@PathVariable UUID id, @PathVariable UUID purchaseId) {
        purchases.cancel(id, purchaseId, currentUser.id());
    }

    @Operation(summary = "Lista as faturas", description = "Da mais recente para a mais antiga. O estado (OPEN, CLOSED, OVERDUE, PAID) é calculado pelas datas e pelo valor pago.")
    @GetMapping("/{id}/invoices")
    public List<InvoiceResponse> invoices(@PathVariable UUID id) {
        return invoices.list(id, currentUser.id());
    }

    @Operation(summary = "Detalha uma fatura", description = "Com as parcelas que a compõem.")
    @GetMapping("/{id}/invoices/{invoiceId}")
    public InvoiceDetailResponse invoice(@PathVariable UUID id, @PathVariable UUID invoiceId) {
        return invoices.detail(id, invoiceId, currentUser.id());
    }

    @Operation(summary = "Paga uma fatura", description = "Aceita pagamento parcial. O pagamento debita a conta, mas não conta como nova despesa. Ao quitar, as parcelas da fatura passam a efetivadas.")
    @ApiResponse(responseCode = "422", description = "Valor maior que o restante, ou conta arquivada.")
    @PostMapping("/{id}/invoices/{invoiceId}/payments")
    public InvoiceResponse pay(@PathVariable UUID id, @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request) {
        return invoices.pay(id, invoiceId, request, currentUser.id());
    }
}
