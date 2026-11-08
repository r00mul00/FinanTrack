package com.finantrack.category.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private CategoryType type;

    @Column(nullable = false, length = 60)
    private String icon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private boolean archived;

    protected Category() {
    }

    public Category(UUID userId, String name, CategoryType type, String icon, String color) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.color = color;
    }

    public void update(String name, String icon, String color, boolean archived) {
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.archived = archived;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public boolean isArchived() {
        return archived;
    }
}
