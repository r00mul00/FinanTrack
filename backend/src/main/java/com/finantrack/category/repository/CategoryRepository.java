package com.finantrack.category.repository;

import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByUserIdOrderByNameAsc(UUID userId);

    List<Category> findAllByUserIdAndTypeOrderByNameAsc(UUID userId, CategoryType type);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTypeAndNameIgnoreCase(UUID userId, CategoryType type, String name);

    boolean existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(UUID userId, CategoryType type, String name, UUID id);
}
