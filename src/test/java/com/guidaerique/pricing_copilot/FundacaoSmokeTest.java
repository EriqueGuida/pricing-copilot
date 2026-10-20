package com.guidaerique.pricing_copilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class FundacaoSmokeTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayDeveTerAplicadoComSucessoAsMigrationsEsperadas() {
        List<String> versoesAplicadas = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = 1 AND version IS NOT NULL",
                String.class);

        // V1 baseline, V2 tabelas do modelo, V3 triggers de imutabilidade.
        // contains (e não isEqualTo): novas migrations não quebram este teste.
        assertThat(versoesAplicadas).contains("1", "2", "3");

        Integer falhas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class);
        assertThat(falhas).isZero();
    }

    @Test
    void deveTerCriadoAsTabelasDoModeloEOsTriggersDeImutabilidade() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tabelas).contains(
                "category", "app_user", "competitor", "product",
                "pricing_run", "competitor_price", "sales_history",
                "price_history", "alert");

        List<String> triggers = jdbc.queryForList(
                "SELECT trigger_name FROM information_schema.triggers "
                        + "WHERE trigger_schema = DATABASE()",
                String.class);

        assertThat(triggers).containsExactlyInAnyOrder(
                "trg_price_history_no_update",
                "trg_price_history_no_delete",
                "trg_competitor_price_no_update",
                "trg_competitor_price_no_delete");
    }
}
