package com.finantrack.audit.entity;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    CANCEL,
    LIMIT_CHANGE,
    LOGIN,
    LOGOUT,
    PASSWORD_RESET,
    REFRESH_TOKEN_REUSE
}
