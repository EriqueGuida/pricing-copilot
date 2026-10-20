package com.guidaerique.pricing_copilot.pricing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PriceGuardTest {

    private final PriceGuard guard = new PriceGuard();

    private final PriceBounds bounds = new PriceBounds(
            new BigDecimal("100.00"),
            new BigDecimal("120.00"),
            false
    );

    @ParameterizedTest
    @CsvSource({
        "100.00",
        "110.00",
        "120.00"
    })
    void deveAceitarPrecoDentroDosLimites(String preco) {
        assertDoesNotThrow(
                () -> guard.validate(new BigDecimal(preco), bounds)
        );
    }

    @ParameterizedTest
    @CsvSource({
        "99.99",
        "120.01",
        "0.00",
        "-10.00"
    })
    void deveRejeitarPrecoForaDosLimites(String preco) {
        assertThrows(
                InvalidPriceSuggestionException.class,
                () -> guard.validate(new BigDecimal(preco), bounds)
        );
    }

    @Test
    void deveAceitarPrecoQuandoIntervaloColapsaEmUmPonto() {
        PriceBounds colapsado = new PriceBounds(
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                true
        );

        assertDoesNotThrow(
                () -> guard.validate(
                        new BigDecimal("125.00"), colapsado
                )
        );

        assertThrows(
                InvalidPriceSuggestionException.class,
                () -> guard.validate(
                        new BigDecimal("125.01"), colapsado
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
        "110.0",
        "110.00",
        "110.000",
        "110",
        "1.1E+2",
        "100.5",
        "119.99"
    })
    void deveAceitarRepresentacoesEquivalentesQueNaoAlteramOValorMonetario(
            String preco
    ) {
        assertDoesNotThrow(
                () -> guard.validate(new BigDecimal(preco), bounds)
        );
    }

    @ParameterizedTest
    @CsvSource({
        "110.005",
        "110.001",
        "100.004",
        "119.999"
    })
    void deveRejeitarPrecoQueExigeArredondamentoParaCentavos(String preco) {
        // 100.004 e 119.999 estão DENTRO do intervalo, mas o banco os
        // arredondaria: o valor gravado diferiria do validado e auditado.
        InvalidPriceSuggestionException ex = assertThrows(
                InvalidPriceSuggestionException.class,
                () -> guard.validate(new BigDecimal(preco), bounds)
        );

        assertTrue(
                ex.getMessage().contains("casas decimais"),
                "A mensagem deve apontar a precisão excessiva: " + ex.getMessage()
        );
    }

    @Test
    void deveRejeitarPrecoForaDoIntervaloMesmoComPrecisaoValida() {
        InvalidPriceSuggestionException ex = assertThrows(
                InvalidPriceSuggestionException.class,
                () -> guard.validate(new BigDecimal("120.01"), bounds)
        );

        assertTrue(
                ex.getMessage().contains("fora do intervalo"),
                "A mensagem deve apontar o intervalo: " + ex.getMessage()
        );
    }

    @Test
    void deveRejeitarPrecoNaoPositivoComMensagemPropria() {
        InvalidPriceSuggestionException ex = assertThrows(
                InvalidPriceSuggestionException.class,
                () -> guard.validate(BigDecimal.ZERO, bounds)
        );

        assertTrue(
                ex.getMessage().contains("positivo"),
                "A mensagem deve apontar preço não positivo: " + ex.getMessage()
        );
    }

    @Test
    void deveRejeitarArgumentosNulos() {
        assertThrows(NullPointerException.class,
                () -> guard.validate(null, bounds));
        assertThrows(NullPointerException.class,
                () -> guard.validate(new BigDecimal("110.00"), null));
    }
}
