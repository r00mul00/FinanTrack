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
    PASSWORD_CHANGE,
    EMAIL_CHANGE,
    DATA_EXPORT,
    DELETION_REQUEST,
    DELETION_CANCEL,
    ACCOUNT_DELETION,
    REFRESH_TOKEN_REUSE
}
