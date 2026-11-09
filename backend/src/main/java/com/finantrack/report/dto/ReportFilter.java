package com.finantrack.report.dto;

import java.time.LocalDate;

/** "month" vale para o relatório mensal, "year" para o anual e "from" e "to" para os demais. */
public record ReportFilter(String month, Integer year, LocalDate from, LocalDate to) {
}
