package com.finantrack.recurring.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.recurring.dto.RecurringRequest;
import com.finantrack.recurring.dto.RecurringResponse;
import com.finantrack.recurring.service.RecurringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/recurring")
@Tag(name = "Contas recorrentes")
public class RecurringController {

    private final RecurringService service;
    private final CurrentUser currentUser;

    public RecurringController(RecurringService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RecurringResponse> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public RecurringResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse create(@Valid @RequestBody RecurringRequest request) {
        return service.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public RecurringResponse update(@PathVariable UUID id, @Valid @RequestBody RecurringRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }

    @PostMapping("/{id}/pause")
    public RecurringResponse pause(@PathVariable UUID id) {
        return service.pause(id, currentUser.id());
    }

    @PostMapping("/{id}/resume")
    public RecurringResponse resume(@PathVariable UUID id) {
        return service.resume(id, currentUser.id());
    }

    @GetMapping("/{id}/upcoming")
    public List<LocalDate> upcoming(@PathVariable UUID id, @RequestParam(defaultValue = "6") int count) {
        return service.upcoming(id, currentUser.id(), count);
    }
}
