package com.finantrack.category.controller;

import com.finantrack.category.dto.CategoryRequest;
import com.finantrack.category.dto.CategoryResponse;
import com.finantrack.category.dto.CategoryUsageResponse;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categorias")
public class CategoryController {

    private final CategoryService service;
    private final CurrentUser currentUser;

    public CategoryController(CategoryService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Lista as categorias")
    @GetMapping
    public List<CategoryResponse> list(@RequestParam(required = false) CategoryType type) {
        return service.list(currentUser.id(), type);
    }

    @Operation(summary = "Detalha uma categoria")
    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.id());
    }

    @Operation(summary = "Conta os registros que usam a categoria", description = "Soma movimentações, orçamentos, compras de cartão e recorrências.")
    @GetMapping("/{id}/usage")
    public CategoryUsageResponse usage(@PathVariable UUID id) {
        return service.usage(id, currentUser.id());
    }

    @Operation(summary = "Cria uma categoria")
    @ApiResponse(responseCode = "409", description = "Já existe uma categoria com este nome e tipo.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request, currentUser.id());
    }

    @Operation(summary = "Altera uma categoria", description = "O tipo (receita ou despesa) não pode mudar.")
    @ApiResponse(responseCode = "409", description = "Já existe uma categoria com este nome e tipo.")
    @ApiResponse(responseCode = "422", description = "Tentativa de mudar o tipo.")
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(id, request, currentUser.id());
    }

    @Operation(summary = "Exclui uma categoria", description = "Se estiver em uso, informe `reassignTo`: os registros passam para essa categoria, na mesma transação.")
    @ApiResponse(responseCode = "409", description = "A categoria está em uso e `reassignTo` não foi informado.")
    @ApiResponse(responseCode = "422", description = "Categoria de destino inválida: a mesma, de outro tipo ou arquivada.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam(required = false) UUID reassignTo) {
        service.delete(id, reassignTo, currentUser.id());
    }
}
