
package com.guidaerique.pricing_copilot.database;

import com.guidaerique.pricing_copilot.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImmutabilityTriggersIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long competitorId;
    private Long priceHistoryId;
    private Long competitorPriceId;
    private Long categoryId;

    @BeforeEach
    void prepararDados() {
        String sufixo = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String nomeCategoria = "Categoria-" + sufixo;
        String sku = "SKU-" + sufixo;
        String nomeConcorrente = "Concorrente-" + sufixo;
        String motivo = "Teste-" + sufixo;

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
            """, productId, motivo);

        priceHistoryId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM price_history
                WHERE product_id = ? AND reason = ?
                ORDER BY id DESC LIMIT 1
                """,
                Long.class,
                productId,
                motivo
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
    void deveImpedirUpdateEDeleteDoHistoricoDePrecos() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    UPDATE price_history
                    SET reason = 'Alteração indevida'
                    WHERE id = ?
                    """, priceHistoryId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    DELETE FROM price_history WHERE id = ?
                    """, priceHistoryId)
        );
    }

    @Test
    void deveImpedirUpdateEDeleteDosPrecosDosConcorrentes() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    UPDATE competitor_price
                    SET price = 130.00
                    WHERE id = ?
                    """, competitorPriceId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    DELETE FROM competitor_price WHERE id = ?
                    """, competitorPriceId)
        );
    }
}
