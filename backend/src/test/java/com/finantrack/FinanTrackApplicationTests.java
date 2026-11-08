package com.finantrack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FinanTrackApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywayAppliesMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '1'", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
