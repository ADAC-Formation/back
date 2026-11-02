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

@SpringBootTest
@ActiveProfiles("dev")
class FlywayMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void flywayMigratedV5AndHibernateOnlyValidates() {

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(flyway.info().current().getState()).isEqualTo(MigrationState.SUCCESS);

        assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto"))
                .isEqualTo("validate");
    }

    @Test
    void flywaySchemaHistoryContainsSuccessfulV1AndV2Migrations() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String version : new String[] {"1", "2"}) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '" + version + "'")) {
                    assertThat(resultSet.next())
                            .as("flyway_schema_history should have a row for version " + version)
                            .isTrue();
                    assertThat(resultSet.getBoolean("success")).isTrue();
                }
            }
        }
    }

    @Test
    void flywaySchemaHistoryContainsSuccessfulV3ThroughV5Migrations() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String version : new String[] {"3", "4", "5"}) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '" + version + "'")) {
                    assertThat(resultSet.next())
                            .as("flyway_schema_history should have a row for version " + version)
                            .isTrue();
                    assertThat(resultSet.getBoolean("success")).isTrue();
                }
            }
        }
    }
}
