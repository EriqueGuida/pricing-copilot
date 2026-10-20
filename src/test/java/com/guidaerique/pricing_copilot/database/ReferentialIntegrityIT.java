package com.guidaerique.pricing_copilot.database;

import com.guidaerique.pricing_copilot.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static com.guidaerique.pricing_copilot.database.SqlAssertions.assertViolacao;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chaves estrangeiras (todas ON UPDATE/DELETE RESTRICT). Cada violação é
 * verificada pelo nome da FK definida em V2__create_core_tables.sql.
 */
@IntegrationTest
class ReferentialIntegrityIT {

    /** Id que nunca existirá: evita a corrida de "MAX(id) + 1". */
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

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
        String sku = "INV-" + UUID.randomUUID()
                .toString().replace("-", "");

        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO product
                        (sku, name, category_id, cost_price, current_price)
                    VALUES (?, 'Produto Inválido', ?, 50.00, 100.00)
                    """, sku, ID_INEXISTENTE),
                "fk_product_category"
        );
    }

    @Test
    void deveImpedirExclusaoDeCategoriaComProdutos() {
        assertViolacao(() ->
                jdbcTemplate.update(
                        "DELETE FROM category WHERE id = ?",
                        categoryId
                ),
                "fk_product_category"
        );
    }

    @Test
    void deveRejeitarHistoricoComProdutoInexistente() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin, reason)
                    VALUES (?, 100.00, 110.00, 'AI', 'Teste inválido')
                    """, ID_INEXISTENTE),
                "fk_history_product"
        );
    }

    @Test
    void deveImpedirExclusaoDeProdutoComHistorico() {
        assertViolacao(() ->
                jdbcTemplate.update(
                        "DELETE FROM product WHERE id = ?",
                        productId
                ),
                "fk_history_product"
        );
    }

    @Test
    void deveRejeitarHistoricoComUsuarioInexistente() {
        // Controle positivo: com um usuário real, o mesmo INSERT é aceito.
        Long usuarioId = criarUsuario();
        int aceito = jdbcTemplate.update("""
            INSERT INTO price_history
                (product_id, old_price, new_price, origin, changed_by, reason)
            VALUES (?, 100.00, 120.00, 'MANUAL', ?, 'Ajuste manual válido')
            """, productId, usuarioId);
        assertEquals(1, aceito);

        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin,
                         changed_by, reason)
                    VALUES (?, 100.00, 130.00, 'MANUAL', ?, 'Autor inexistente')
                    """, productId, ID_INEXISTENTE),
                "fk_history_user"
        );
    }

    @Test
    void deveImpedirExclusaoDeUsuarioComHistorico() {
        Long usuarioId = criarUsuario();
        jdbcTemplate.update("""
            INSERT INTO price_history
                (product_id, old_price, new_price, origin, changed_by, reason)
            VALUES (?, 100.00, 120.00, 'MANUAL', ?, 'Ajuste manual')
            """, productId, usuarioId);

        assertViolacao(() ->
                jdbcTemplate.update(
                        "DELETE FROM app_user WHERE id = ?",
                        usuarioId
                ),
                "fk_history_user"
        );
    }

    @Test
    void deveRejeitarHistoricoComExecucaoDePrecificacaoInexistente() {
        assertViolacao(() ->
                jdbcTemplate.update("""
                    INSERT INTO price_history
                        (product_id, old_price, new_price, origin,
                         pricing_run_id, reason)
                    VALUES (?, 100.00, 140.00, 'AI', ?, 'Execução inexistente')
                    """, productId, ID_INEXISTENTE),
                "fk_history_run"
        );
    }

    private Long criarUsuario() {
        String email = "usuario-" + UUID.randomUUID() + "@exemplo.com";

        jdbcTemplate.update("""
            INSERT INTO app_user (email, password_hash, role)
            VALUES (?, 'hash-bcrypt', 'ADMIN')
            """, email);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?",
                Long.class,
                email
        );
    }
}
