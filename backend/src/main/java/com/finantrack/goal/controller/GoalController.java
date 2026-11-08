package com.finantrack.goal.controller;

import com.finantrack.common.dto.PageResponse;
import com.finantrack.common.security.CurrentUser;
import com.finantrack.goal.dto.GoalMovementRequest;
import com.finantrack.goal.dto.GoalMovementResponse;
import com.finantrack.goal.dto.GoalRequest;
import com.finantrack.goal.dto.GoalResponse;
import com.finantrack.goal.service.GoalService;
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
@RequestMapping("/api/v1/goals")
@Tag(name = "Metas")
public class GoalController {

    private final GoalService service;
    private final CurrentUser currentUser;

    public GoalController(GoalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GoalResponse> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return service.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id, currentUser.id());
    }

    @PostMapping("/{id}/deposits")
    public GoalResponse deposit(@PathVariable UUID id, @Valid @RequestBody GoalMovementRequest request) {
        return service.deposit(id, request, currentUser.id());
    }

    @PostMapping("/{id}/withdrawals")
    public GoalResponse withdraw(@PathVariable UUID id, @Valid @RequestBody GoalMovementRequest request) {
        return service.withdraw(id, request, currentUser.id());
    }

    @PostMapping("/{id}/complete")
    public GoalResponse complete(@PathVariable UUID id) {
        return service.complete(id, currentUser.id());
    }

    @GetMapping("/{id}/movements")
    public PageResponse<GoalMovementResponse> movements(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.movements(id, currentUser.id(), page, size);
    }
}
