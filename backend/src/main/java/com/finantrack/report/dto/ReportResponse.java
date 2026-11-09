package com.finantrack.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Formato único de todos os relatórios: a tela, o CSV e o PDF leem este mesmo objeto,
 * por isso os totais são sempre iguais. Cada célula é um texto no formato canônico da
 * coluna: "1234.50" em valores e percentuais e "2026-03-10" em datas.
 */
public record ReportResponse(String title, LocalDate from, LocalDate to, List<Indicator> indicators,
        List<Column> columns, List<List<String>> rows, List<String> totals) {

    public record Indicator(String label, BigDecimal value) {
    }

    public record Column(String label, ColumnKind kind) {
    }

    public enum ColumnKind {
        TEXT,
        DATE,
        MONEY,
        PERCENT,
        INTEGER
    }
}
