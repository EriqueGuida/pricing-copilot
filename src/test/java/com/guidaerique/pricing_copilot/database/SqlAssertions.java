package com.guidaerique.pricing_copilot.database;

import org.junit.jupiter.api.function.Executable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica a CAUSA REAL de uma falha SQL, e não apenas que "algo falhou".
 *
 * Um assertThrows genérico passaria também com SQL incorreto, coluna
 * inexistente ou banco fora do ar. Aqui a mensagem da causa raiz (a do
 * MySQL) precisa citar a constraint, o trigger ou a coluna esperada. Um erro
 * de infraestrutura (conexão, por exemplo) NÃO contém o trecho e, portanto,
 * reprova o teste.
 *
 * O trecho esperado é o nome da constraint/trigger (estável, definido nas
 * migrations) ou a mensagem do nosso próprio SIGNAL, e não o texto completo
 * do driver, que varia entre versões.
 */
final class SqlAssertions {

    private SqlAssertions() {
    }

    static void assertViolacao(Executable acao, String trechoEsperado) {
        DataAccessException excecao =
                assertThrows(DataAccessException.class, acao);

        String causaRaiz = NestedExceptionUtils
                .getMostSpecificCause(excecao)
                .getMessage();

        assertTrue(
                causaRaiz != null && causaRaiz.contains(trechoEsperado),
                () -> "Falhou, mas por outro motivo. Esperado conter '"
                        + trechoEsperado + "', causa raiz: " + causaRaiz
        );
    }
}
