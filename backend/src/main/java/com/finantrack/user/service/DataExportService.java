package com.finantrack.user.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.common.exception.ResourceNotFoundException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cópia dos dados pessoais. Hash de senha e tokens de sessão não fazem parte dela. */
@Service
public class DataExportService {

    private static final String PROFILE = """
            select id, name, email, currency, theme, notify_budget, notify_due_dates, created_at
              from users where id = ?
            """;
    private static final String CARDS = "(select id from credit_cards where user_id = ?)";

    private static final Map<String, String> SECTIONS = sections();

    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;

    public DataExportService(JdbcTemplate jdbc, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> export(UUID userId) {
        List<Map<String, Object>> profile = rows(PROFILE, userId);
        if (profile.isEmpty()) {
            throw new ResourceNotFoundException("Usuário não encontrado.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile.getFirst());
        SECTIONS.forEach((name, sql) -> result.put(name, rows(sql, userId)));
        audit.record(userId, AuditAction.DATA_EXPORT, AuditEntityType.USER, userId);
        return result;
    }

    private List<Map<String, Object>> rows(String sql, UUID userId) {
        return jdbc.queryForList(sql, userId).stream().map(this::readable).toList();
    }

    /** Datas em ISO-8601; nulos ficam de fora para o arquivo não crescer à toa. */
    private Map<String, Object> readable(Map<String, Object> row) {
        return row.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> readable(entry.getValue()),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private Object readable(Object value) {
        return switch (value) {
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case Date date -> date.toString();
            case UUID id -> id.toString();
            default -> value;
        };
    }

    private static Map<String, String> sections() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("accounts", ownedBy("accounts"));
        sections.put("categories", ownedBy("categories"));
        sections.put("transactions", ownedBy("transactions"));
        sections.put("budgets", ownedBy("budgets"));
        sections.put("goals", ownedBy("financial_goals"));
        sections.put("goalMovements", "select * from goal_movements where goal_id in "
                + "(select id from financial_goals where user_id = ?) order by created_at");
        sections.put("cards", ownedBy("credit_cards"));
        sections.put("cardInvoices", "select * from credit_card_invoices where card_id in " + CARDS + " order by created_at");
        sections.put("cardPurchases", "select * from credit_card_purchases where card_id in " + CARDS + " order by created_at");
        sections.put("cardInstallments", "select * from credit_card_installments where purchase_id in "
                + "(select id from credit_card_purchases where card_id in " + CARDS + ") order by created_at");
        sections.put("recurring", ownedBy("recurring_transactions"));
        sections.put("notifications", ownedBy("notifications"));
        return sections;
    }

    private static String ownedBy(String table) {
        return "select * from " + table + " where user_id = ? order by created_at";
    }
}
