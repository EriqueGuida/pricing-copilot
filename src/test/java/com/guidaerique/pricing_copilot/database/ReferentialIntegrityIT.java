
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
class ReferentialIntegrityIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryId;
    private Long productId;

    @BeforeEach
    void prepararDados() {
        String sufixo = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String nomeCategoria = "Categoria-" + sufixo;
        String sku = "SKU-" + sufixo;

        jdbcTemplate.update("""
            INSERT INTO category
                (name, vertical, min_margin, max_margin, max_variation)
            VALUES (?, 'CONSUMER_ELECTRONICS',
                    0.1000, 0.5000, 0.2000)
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
            INSERT INTO price_history
                (product_id, old_price, new_price, origin, reason)
            VALUES (?, 100.00, 110.00, 'AI', ?)
            """, productId, "Teste-" + sufixo);
    }

    @Test
    void deveRejeitarProdutoComCategoriaInexistente() {
        Long categoriaInexistente = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM category",
                Long.class
        );

        String sku = "INV-" + UUID.randomUUID()
                .toString().replace("-", "");

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO product
                        (sku, name, category_id, cost_price, current_price)
                    VALUES (?, 'Produto Inválido', ?, 50.00, 100.00)
                    """, sku, categoriaInexistente)
        );
    }

    @Test
    void deveImpedirExclusaoDeCategoriaComProdutos() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "DELETE FROM category WHERE id = ?",
                        categoryId
                )
        );
    }

    @Test
    void deveRejeitarHistoricoComProdutoInexistente() {
        Long produtoInexistente = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM product",
                Long.class
        );

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin, reason)
                    VALUES (?, 100.00, 110.00, 'AI', 'Teste inválido')
                    """, produtoInexistente)
        );
    }

    @Test
    void deveImpedirExclusaoDeProdutoComHistorico() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "DELETE FROM product WHERE id = ?",
                        productId
                )
        );
    }
}
