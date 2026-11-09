package com.finantrack.report.dto;

import com.finantrack.common.exception.ResourceNotFoundException;
import java.util.Arrays;

public enum ReportType {
    MONTHLY("monthly"),
    ANNUAL("annual"),
    BY_CATEGORY("by-category"),
    BY_PERIOD("by-period"),
    INCOME_VS_EXPENSE("income-vs-expense"),
    EVOLUTION("evolution"),
    BY_CARD("by-card");

    private final String slug;

    ReportType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static ReportType fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(type -> type.slug.equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Relatório não encontrado."));
    }
}
