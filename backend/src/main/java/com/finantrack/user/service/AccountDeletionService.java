package com.finantrack.user.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.auth.service.EmailSender;
import com.finantrack.auth.service.RefreshTokenService;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exclusão com carência de 30 dias; regras em docs/06. */
@Service
public class AccountDeletionService {

    static final Duration GRACE_PERIOD = Duration.ofDays(30);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CARDS = "(select id from credit_cards where user_id = ?)";

    /** Ordem imposta pelas FKs ON DELETE RESTRICT: dependentes antes das tabelas de origem. */
    private static final List<String> PURGE_STATEMENTS = List.of(
            "delete from notifications where user_id = ?",
            "delete from goal_movements where goal_id in (select id from financial_goals where user_id = ?)",
            "delete from financial_goals where user_id = ?",
            "delete from credit_card_installments where purchase_id in "
                    + "(select id from credit_card_purchases where card_id in " + CARDS + ")",
            "delete from credit_card_purchases where card_id in " + CARDS,
            "delete from transactions where user_id = ?",
            "delete from credit_card_invoices where card_id in " + CARDS,
            "delete from credit_cards where user_id = ?",
            "delete from recurring_transactions where user_id = ?",
            "delete from budgets where user_id = ?",
            "delete from accounts where user_id = ?",
            "delete from categories where user_id = ?",
            "delete from refresh_tokens where user_id = ?",
            "delete from user_tokens where user_id = ?",
            "delete from users where id = ?");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailSender emailSender;
    private final AuditRecorder audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, EmailSender emailSender, AuditRecorder audit, JdbcTemplate jdbc,
            Clock clock, FinanTrackProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailSender = emailSender;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = properties.zoneId();
    }

    @Transactional
    public void request(UUID userId, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Senha atual incorreta.");
        }
        if (user.getDeletionRequestedAt() != null) {
            return;
        }

        Instant now = clock.instant();
        user.requestDeletion(now);
        refreshTokenService.revokeAllSessions(userId);
        audit.record(userId, AuditAction.DELETION_REQUEST, AuditEntityType.USER, userId);

        emailSender.send(user.getEmail(), "Exclusão da sua conta FinanTrack", """
                Olá, %s!

                Recebemos o pedido de exclusão da sua conta. Seus dados serão apagados a partir de %s.

                Para cancelar, basta entrar no FinanTrack até essa data.
                """.formatted(user.getName(), now.plus(GRACE_PERIOD).atZone(zone).format(DATE)));
    }

    /** Entrar na conta durante a carência desfaz o pedido. */
    @Transactional
    public void cancelOnLogin(User user) {
        if (user.getDeletionRequestedAt() == null
                || userRepository.cancelDeletionRequest(user.getId(), clock.instant()) == 0) {
            return;
        }
        audit.record(user.getId(), AuditAction.DELETION_CANCEL, AuditEntityType.USER, user.getId());
        emailSender.send(user.getEmail(), "Exclusão da sua conta FinanTrack cancelada", """
                Olá, %s!

                Você entrou na sua conta e o pedido de exclusão foi cancelado. Seus dados continuam guardados.
                """.formatted(user.getName()));
    }

    @Transactional(readOnly = true)
    public List<UUID> findDue(Instant now) {
        return userRepository.findIdsWithDeletionRequestedUntil(now.minus(GRACE_PERIOD));
    }

    /**
     * Apaga tudo em uma transação. O lock e a nova conferência do prazo evitam
     * apagar quem acabou de cancelar. A trilha de auditoria permanece.
     */
    @Transactional
    public boolean purge(UUID userId, Instant now) {
        boolean stillDue = !jdbc.queryForList(
                "select id from users where id = ? and deletion_requested_at <= ? for update",
                userId, Timestamp.from(now.minus(GRACE_PERIOD))).isEmpty();
        if (!stillDue) {
            return false;
        }
        PURGE_STATEMENTS.forEach(statement -> jdbc.update(statement, userId));
        audit.record(userId, AuditAction.ACCOUNT_DELETION, AuditEntityType.USER, userId);
        return true;
    }
}
