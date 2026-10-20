package com.guidaerique.pricing_copilot.database;

import com.guidaerique.pricing_copilot.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.guidaerique.pricing_copilot.database.SqlAssertions.assertViolacao;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Constraints de negócio do schema (CHECK, UNIQUE e ENUM).
 *
 * Cada violação é verificada pela constraint esperada (nome definido em
 * V2__create_core_tables.sql), nunca por um erro genérico.
 */
@IntegrationTest
class BusinessConstraintsIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryId;
    private Long productId;
    private String nomeCategoria;
    private String sku;

    @BeforeEach
    void prepararDados() {
        String sufixo = UUID.randomUUID()
                .toString()
                .replace("-", "");

        nomeCategoria = "Categoria-" + sufixo;
        sku = "SKU-" + sufixo;

        jdbcTemplate.update("""
            INSERT INTO category
                (name, vertical, min_margin, max_margin, max_variation)
            VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 0.5000, 0.2000)
            """, nomeCategoria);

        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = ?",
                Long.class,
                nomeCategoria
        );

        jdbcTemplate.update("""
            INSERT INTO product
                (sku, name, category_id, cost_price, current_price)
            VALUES (?, ?, ?, 50.00, 100.00)
            """, sku, "Produto-" + sufixo, categoryId);

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku = ?",
                Long.class,
                sku
        );
    }

    // ---------------------------------------------------------------
    // CHECK: categoria (margens e variação)
    // ---------------------------------------------------------------

    @Test
    void deveRejeitarMargensInvalidas() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS', 0.5000, 0.5000, 0.2000)
                    """, "Margem-Igual-" + UUID.randomUUID()),
                "ck_category_margins"
        );

        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 1.0000, 0.2000)
                    """, "Margem-Maior-" + UUID.randomUUID()),
                "ck_category_margins"
        );
    }

    @Test
    void deveRejeitarVariacaoForaDoIntervalo() {
        assertViolacao(() ->
                jdbcTemplate.update(
                        "INSERT INTO category " +
                        "(name, vertical, min_margin, max_margin, max_variation) " +
                        "VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 0.5000, 0.0000)",
                        "Variacao-Zero-" + UUID.randomUUID()
                ),
                "ck_category_variation"
        );

        assertViolacao(() ->
                jdbcTemplate.update(
                        "INSERT INTO category " +
                        "(name, vertical, min_margin, max_margin, max_variation) " +
                        "VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 0.5000, 0.5001)",
                        "Variacao-Alta-" + UUID.randomUUID()
                ),
                "ck_category_variation"
        );
    }
}