package com.finantrack.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Confere, no PostgreSQL, que unicidade e chave estrangeira são distinguidas. */
@SpringBootTest
@ActiveProfiles("test")
class UniqueConstraintsIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void recognizesUniqueViolation() {
        accountRepository.saveAndFlush(new Account(ana, "Nubank", AccountType.CHECKING, BigDecimal.ZERO));

        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(new Account(ana, "NUBANK", AccountType.WALLET, BigDecimal.ZERO)));

        assertThat(UniqueConstraints.isViolated(ex)).isTrue();
    }

    @Test
    void foreignKeyViolationIsNotTakenForDuplicate() {
        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(
                        new Account(UUID.randomUUID(), "Órfã", AccountType.CHECKING, BigDecimal.ZERO)));

        assertThat(ex).isNotNull();
        assertThat(UniqueConstraints.isViolated(ex)).isFalse();
    }
}
