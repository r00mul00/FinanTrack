package com.finantrack.category.service;

import com.finantrack.category.dto.CategoryRequest;
import com.finantrack.category.dto.CategoryResponse;
import com.finantrack.category.dto.CategoryUsageResponse;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.mapper.CategoryMapper;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.common.exception.UniqueConstraints;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final String DUPLICATE_NAME = "Já existe uma categoria com este nome.";

    private record DefaultCategory(String name, CategoryType type, String icon, String color) {
    }

    /** Mantida igual à carga de usuários antigos em V6__create_categories.sql. */
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Alimentação", CategoryType.EXPENSE, "Restaurant", "#FF8A65"),
            new DefaultCategory("Moradia", CategoryType.EXPENSE, "Home", "#7C4DFF"),
            new DefaultCategory("Transporte", CategoryType.EXPENSE, "DirectionsCar", "#4FC3F7"),
            new DefaultCategory("Saúde", CategoryType.EXPENSE, "LocalHospital", "#FF647C"),
            new DefaultCategory("Educação", CategoryType.EXPENSE, "School", "#FFB547"),
            new DefaultCategory("Lazer", CategoryType.EXPENSE, "SportsEsports", "#B14DDB"),
            new DefaultCategory("Compras", CategoryType.EXPENSE, "ShoppingBag", "#F06292"),
            new DefaultCategory("Contas", CategoryType.EXPENSE, "Receipt", "#90A4AE"),
            new DefaultCategory("Outros", CategoryType.EXPENSE, "MoreHoriz", "#A6A8BC"),
            new DefaultCategory("Salário", CategoryType.INCOME, "Payments", "#20C997"),
            new DefaultCategory("Freelance", CategoryType.INCOME, "Work", "#4DB6AC"),
            new DefaultCategory("Investimentos", CategoryType.INCOME, "TrendingUp", "#81C784"),
            new DefaultCategory("Outros", CategoryType.INCOME, "AttachMoney", "#A6A8BC"));

    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final List<CategoryReferenceHandler> referenceHandlers;

    public CategoryService(CategoryRepository repository, CategoryMapper mapper,
            List<CategoryReferenceHandler> referenceHandlers) {
        this.repository = repository;
        this.mapper = mapper;
        this.referenceHandlers = referenceHandlers;
    }

    /** Sempre dentro da transação do cadastro: ou cria usuário e categorias, ou nada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createDefaults(UUID userId) {
        repository.saveAll(DEFAULTS.stream()
                .map(item -> new Category(userId, item.name(), item.type(), item.icon(), item.color()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, CategoryType type) {
        List<Category> categories = type == null
                ? repository.findAllByUserIdOrderByNameAsc(userId)
                : repository.findAllByUserIdAndTypeOrderByNameAsc(userId, type);
        return categories.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id, UUID userId) {
        return mapper.toResponse(find(id, userId));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, UUID userId) {
        if (repository.existsByUserIdAndTypeAndNameIgnoreCase(userId, request.type(), request.name())) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        Category category = new Category(userId, request.name(), request.type(), request.icon(), request.color());
        return mapper.toResponse(saveChecked(category));
    }

    /** O tipo não muda depois de criado: vínculos existentes ficariam incoerentes. */
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request, UUID userId) {
        Category category = find(id, userId);
        if (category.getType() != request.type()) {
            throw new BusinessRuleException("O tipo da categoria não pode ser alterado.");
        }
        if (repository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(userId, category.getType(), request.name(), id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        category.update(request.name(), request.icon(), request.color(), request.archived());
        return mapper.toResponse(saveChecked(category));
    }

    @Transactional(readOnly = true)
    public CategoryUsageResponse usage(UUID id, UUID userId) {
        return new CategoryUsageResponse(countReferences(find(id, userId).getId()));
    }

    /** Categoria em uso exige um destino para os vínculos, tudo na mesma transação. */
    @Transactional
    public void delete(UUID id, UUID reassignTo, UUID userId) {
        Category category = find(id, userId);

        if (countReferences(id) > 0) {
            if (reassignTo == null) {
                throw new ConflictException("Esta categoria está em uso. Escolha outra para receber os registros.");
            }
            Category target = findTarget(category, reassignTo, userId);
            referenceHandlers.forEach(handler -> handler.reassign(id, target.getId()));
        }
        repository.delete(category);
    }

    private Category findTarget(Category source, UUID targetId, UUID userId) {
        Category target = find(targetId, userId);
        if (target.getId().equals(source.getId())) {
            throw new BusinessRuleException("Escolha uma categoria diferente da que será excluída.");
        }
        if (target.getType() != source.getType()) {
            throw new BusinessRuleException("A categoria de destino precisa ser do mesmo tipo.");
        }
        if (target.isArchived()) {
            throw new BusinessRuleException("A categoria de destino está arquivada.");
        }
        return target;
    }

    private long countReferences(UUID categoryId) {
        return referenceHandlers.stream().mapToLong(handler -> handler.countReferences(categoryId)).sum();
    }

    private Category find(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
    }

    private Category saveChecked(Category category) {
        try {
            return repository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolated(ex)) {
                throw new ConflictException(DUPLICATE_NAME);
            }
            throw ex;
        }
    }
}
