package com.finantrack.audit.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.entity.AuditLog;
import com.finantrack.audit.repository.AuditLogRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava na transação de quem chama: se a alteração for desfeita, o registro também é.
 * Quem chama informa só os campos auditáveis: nunca senhas, tokens ou dados completos de cartão.
 */
@Component
public class AuditRecorder {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditRecorder(AuditLogRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID userId, AuditAction action, AuditEntityType entityType, UUID entityId, Object before,
            Object after) {
        repository.save(new AuditLog(userId, action, entityType, entityId, toJson(before, after), clock.instant()));
    }

    /** Evento sem valores anterior e novo, como os de segurança. */
    @Transactional
    public void record(UUID userId, AuditAction action, AuditEntityType entityType, UUID entityId) {
        record(userId, action, entityType, entityId, null, null);
    }

    private String toJson(Object before, Object after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (before != null) {
            changes.put("before", before);
        }
        if (after != null) {
            changes.put("after", after);
        }
        return changes.isEmpty() ? null : jsonMapper.writeValueAsString(changes);
    }
}
