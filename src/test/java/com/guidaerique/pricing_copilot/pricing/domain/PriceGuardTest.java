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
}