package com.adac.portail;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flyway actually ran V1 against the datasource, and that Hibernate is only
 * validating (never creating/updating) the schema Flyway owns.
 */
@SpringBootTest
@ActiveProfiles("dev")
class FlywayMigrationTest {

    @Autowired
    private DataSource dataSource;

    // No Flyway bean exists at all before this ticket's dependencies/config land — injection
    // itself fails and the context fails to start, which is exactly the RED we want (see
    // TICKET-004's own test plan).
    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void flywayMigratedV2AndHibernateOnlyValidates() {
        // "2", not "1": TICKET-046 adds V2__add_categories.sql on top of V1 — current() is the
        // latest applied version, so this also proves V2 actually ran (not just present on disk).
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().current().getState()).isEqualTo(MigrationState.SUCCESS);

        // Asserting the runtime bean, not just the YAML property: this fails if Hibernate's
        // DDL mode were ever overridden via hibernate.hbm2ddl.auto — the "never update/create
        // in production" regression this ticket exists to guard against.
        assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto"))
                .isEqualTo("validate");
    }

    @Test
    void flywaySchemaHistoryContainsSuccessfulV1Migration() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT success FROM flyway_schema_history WHERE version = '1'")) {

            assertThat(resultSet.next())
                    .as("flyway_schema_history should have a row for version 1")
                    .isTrue();
            assertThat(resultSet.getBoolean("success")).isTrue();
        }
    }

    @Test
    void flywaySchemaHistoryContainsSuccessfulV2Migration() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT success FROM flyway_schema_history WHERE version = '2'")) {

            assertThat(resultSet.next())
                    .as("flyway_schema_history should have a row for version 2")
                    .isTrue();
            assertThat(resultSet.getBoolean("success")).isTrue();
        }
    }
}
