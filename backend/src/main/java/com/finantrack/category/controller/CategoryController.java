package com.finantrack.category.controller;

import com.finantrack.category.dto.CategoryRequest;
import com.finantrack.category.dto.CategoryResponse;
import com.finantrack.category.dto.CategoryUsageResponse;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categorias")
public class CategoryController {

    private final CategoryService service;
    private final CurrentUser currentUser;

    public CategoryController(CategoryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(required = false) CategoryType type) {
        return service.list(currentUser.id(), type);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @GetMapping("/{id}/usage")
    public CategoryUsageResponse usage(@PathVariable UUID id) {
        return service.usage(id, currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam(required = false) UUID reassignTo) {
        service.delete(id, reassignTo, currentUser.id());
    }
}
