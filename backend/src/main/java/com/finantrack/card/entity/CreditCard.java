package com.finantrack.card.entity;

import com.finantrack.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Guarda apenas os quatro últimos dígitos: nunca número completo, CVV ou senha. */
@Entity
@Table(name = "credit_cards")
public class CreditCard extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false)
    private boolean archived;

    @Version
    @Column(nullable = false)
    private long version;

    protected CreditCard() {
    }

    public CreditCard(UUID userId, String nickname, CardBrand brand, String lastFour, BigDecimal creditLimit,
            int closingDay, int dueDay) {
        this.userId = userId;
        this.nickname = nickname;
        this.brand = brand;
        this.lastFour = lastFour;
        this.creditLimit = creditLimit;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
    }

    public void update(String nickname, CardBrand brand, String lastFour, BigDecimal creditLimit, int closingDay,
            int dueDay, boolean archived) {
        this.nickname = nickname;
        this.brand = brand;
        this.lastFour = lastFour;
        this.creditLimit = creditLimit;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.archived = archived;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public CardBrand getBrand() {
        return brand;
    }

    public String getLastFour() {
        return lastFour;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public int getClosingDay() {
        return closingDay;
    }

    public int getDueDay() {
        return dueDay;
    }

    public boolean isArchived() {
        return archived;
    }
}
