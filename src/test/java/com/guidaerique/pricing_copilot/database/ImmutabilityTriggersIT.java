package com.guidaerique.pricing_copilot.database;

import com.guidaerique.pricing_copilot.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static com.guidaerique.pricing_copilot.database.SqlAssertions.assertViolacao;
import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class ImmutabilityTriggersIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long competitorId;
    private Long priceHistoryId;
    private Long competitorPriceId;
    private Long categoryId;
    private String motivoOriginal;

    @BeforeEach
    void prepararDados() {
        String sufixo = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String nomeCategoria = "Categoria-" + sufixo;
        String sku = "SKU-" + sufixo;
        String nomeConcorrente = "Concorrente-" + sufixo;
        motivoOriginal = "Teste-" + sufixo;

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

        jdbcTemplate.update("""
            INSERT INTO competitor (name)
            VALUES (?)
            """, nomeConcorrente);

        competitorId = jdbcTemplate.queryForObject(
                "SELECT id FROM competitor WHERE name = ?",
                Long.class,
                nomeConcorrente
        );

        jdbcTemplate.update("""
            INSERT INTO price_history
                (product_id, old_price, new_price, origin, reason)
            VALUES (?, 100.00, 110.00, 'AI', ?)
            """, productId, motivoOriginal);

        priceHistoryId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM price_history
                WHERE product_id = ? AND reason = ?
                ORDER BY id DESC LIMIT 1
                """,
                Long.class,
                productId,
                motivoOriginal
        );

        jdbcTemplate.update("""
            INSERT INTO competitor_price
                (product_id, competitor_id, price, source)
            VALUES (?, ?, 120.00, 'CSV')
            """, productId, competitorId);

        competitorPriceId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM competitor_price
                WHERE product_id = ? AND competitor_id = ?
                ORDER BY id DESC LIMIT 1
                """,
                Long.class,
                productId,
                competitorId
        );
    }

    @Test
    void deveImpedirUpdateDoHistoricoDePrecosEPreservarORegistro() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    UPDATE price_history
                    SET reason = 'Alteração indevida'
                    WHERE id = ?
                    """, priceHistoryId),
                "price_history e imutavel: UPDATE proibido"
        );

        assertHistoricoIntegro();
    }

    @Test
    void deveImpedirDeleteDoHistoricoDePrecosEPreservarORegistro() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    DELETE FROM price_history WHERE id = ?
                    """, priceHistoryId),
                "price_history e imutavel: DELETE proibido"
        );

        assertHistoricoIntegro();
    }

    @Test
    void deveImpedirUpdateDosPrecosDosConcorrentesEPreservarORegistro() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    UPDATE competitor_price
                    SET price = 130.00
                    WHERE id = ?
                    """, competitorPriceId),
                "competitor_price e imutavel: UPDATE proibido"
        );

        assertPrecoDeConcorrenteIntegro();
    }

    @Test
    void deveImpedirDeleteDosPrecosDosConcorrentesEPreservarORegistro() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    DELETE FROM competitor_price WHERE id = ?
                    """, competitorPriceId),
                "competitor_price e imutavel: DELETE proibido"
        );

        assertPrecoDeConcorrenteIntegro();
    }

    /** O registro rejeitado continua existindo e com os valores originais. */
    private void assertHistoricoIntegro() {
        Integer linhas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_history WHERE id = ?",
                Integer.class,
                priceHistoryId
        );
        assertEquals(1, linhas, "O registro do histórico deveria existir.");

        String motivo = jdbcTemplate.queryForObject(
                "SELECT reason FROM price_history WHERE id = ?",
                String.class,
                priceHistoryId
        );
        assertEquals(motivoOriginal, motivo, "O motivo foi alterado.");

        BigDecimal novoPreco = jdbcTemplate.queryForObject(
                "SELECT new_price FROM price_history WHERE id = ?",
                BigDecimal.class,
                priceHistoryId
        );
        assertEquals(
                0,
                new BigDecimal("110.00").compareTo(novoPreco),
                "O preço do histórico foi alterado."
        );
    }

    private void assertPrecoDeConcorrenteIntegro() {
        Integer linhas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM competitor_price WHERE id = ?",
                Integer.class,
                competitorPriceId
        );
        assertEquals(1, linhas, "O preço do concorrente deveria existir.");

        BigDecimal preco = jdbcTemplate.queryForObject(
                "SELECT price FROM competitor_price WHERE id = ?",
                BigDecimal.class,
                competitorPriceId
        );
        assertEquals(
                0,
                new BigDecimal("120.00").compareTo(preco),
                "O preço do concorrente foi alterado."
        );
    }
}
