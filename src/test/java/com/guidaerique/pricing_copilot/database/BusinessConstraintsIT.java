
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
class BusinessConstraintsIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void prepararDados() {
        String sufixo = UUID.randomUUID()
                .toString()
                .replace("-", "");

        jdbcTemplate.update("""
            INSERT INTO category
                (name, vertical, min_margin, max_margin, max_variation)
            VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 0.5000, 0.2000)
            """, "Categoria-" + sufixo);

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = ?",
                Long.class,
                "Categoria-" + sufixo
        );

        jdbcTemplate.update("""
            INSERT INTO product
                (sku, name, category_id, cost_price, current_price)
            VALUES (?, ?, ?, 50.00, 100.00)
            """, "SKU-" + sufixo, "Produto-" + sufixo, categoryId);

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku = ?",
                Long.class,
                "SKU-" + sufixo
        );
    }

    @Test
    void deveRejeitarMargensInvalidas() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS', 0.5000, 0.5000, 0.2000)
                    """, "Margem-Igual-" + UUID.randomUUID())
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS', 0.1000, 1.0000, 0.2000)
                    """, "Margem-Maior-" + UUID.randomUUID())
        );
    }

    @Test
    void deveRejeitarVariacaoForaDoIntervalo() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS',
                            0.1000, 0.5000, 0.0000)
                    """, "Variacao-Zero-" + UUID.randomUUID())
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO category
                        (name, vertical, min_margin, max_margin, max_variation)
                    VALUES (?, 'CONSUMER_ELECTRONICS',
                            0.1000, 0.5000, 0.5001)
                    """, "Variacao-Alta-" + UUID.randomUUID())
        );
    }

    @Test
    void deveRejeitarPrecosNaoPositivosNoProduto() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    UPDATE product SET cost_price = 0 WHERE id = ?
                    """, productId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    UPDATE product SET current_price = -1 WHERE id = ?
                    """, productId)
        );
    }

    @Test
    void deveRejeitarPrecoDeConcorrenteNaoPositivo() {
        Long competitorId = criarConcorrente();

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO competitor_price
                        (product_id, competitor_id, price, source)
                    VALUES (?, ?, 0.00, 'CSV')
                    """, productId, competitorId)
        );
    }

    @Test
    void deveRejeitarQuantidadeEPrecoDeVendaInvalidos() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO sales_history
                        (product_id, sold_on, quantity, unit_price)
                    VALUES (?, CURRENT_DATE, 0, 100.00)
                    """, productId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO sales_history
                        (product_id, sold_on, quantity, unit_price)
                    VALUES (?, CURRENT_DATE, 1, 0.00)
                    """, productId)
        );
    }

    @Test
    void deveRejeitarHistoricoDePrecoInvalido() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin, reason)
                    VALUES (?, 0.00, 110.00, 'AI', 'Teste')
                    """, productId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin, reason)
                    VALUES (?, 100.00, 100.00, 'AI', 'Teste')
                    """, productId)
        );
    }

    @Test
    void deveRejeitarEstadoDeAlertaInconsistente() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO alert
                        (product_id, type, status, message, resolved_at)
                    VALUES (?, 'AI_UNAVAILABLE', 'OPEN',
                            'Alerta de teste', CURRENT_TIMESTAMP(3))
                    """, productId)
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO alert
                        (product_id, type, status, message, resolved_at)
                    VALUES (?, 'AI_UNAVAILABLE', 'RESOLVED',
                            'Alerta de teste', NULL)
                    """, productId)
        );
    }

    private Long criarConcorrente() {
        String nome = "Concorrente-" + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO competitor (name) VALUES (?)",
                nome
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM competitor WHERE name = ?",
                Long.class,
                nome
        );
    }
}
