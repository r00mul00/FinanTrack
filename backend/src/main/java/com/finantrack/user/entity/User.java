package com.finantrack.user.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Theme theme = Theme.DARK;

    @Column(name = "notify_budget", nullable = false)
    private boolean notifyBudget = true;

    @Column(name = "notify_due_dates", nullable = false)
    private boolean notifyDueDates = true;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    protected User() {
    }

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(Instant when) {
        this.emailVerifiedAt = when;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    /** Recebe o e-mail já normalizado e confirmado pelo link enviado ao novo endereço. */
    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void changeCurrency(String newCurrency) {
        this.currency = newCurrency;
    }

    public void changeTheme(Theme newTheme) {
        this.theme = newTheme;
    }

    public void changeNotifyBudget(boolean enabled) {
        this.notifyBudget = enabled;
    }

    public void changeNotifyDueDates(boolean enabled) {
        this.notifyDueDates = enabled;
    }

    public void requestDeletion(Instant when) {
        this.deletionRequestedAt = when;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public Theme getTheme() {
        return theme;
    }

    public boolean isNotifyBudget() {
        return notifyBudget;
    }

    public boolean isNotifyDueDates() {
        return notifyDueDates;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }
}
