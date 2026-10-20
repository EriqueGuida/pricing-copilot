package com.guidaerique.pricing_copilot.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceBoundsTest {

    private static final BigDecimal CEM = new BigDecimal("100.00");
    private static final BigDecimal CENTO_E_VINTE = new BigDecimal("120.00");

    @Test
    void deveAceitarPisoIgualAoTeto() {
        assertDoesNotThrow(() -> new PriceBounds(CEM, CEM, true));
    }

    @Test
    void deveRejeitarLimitesNulos() {
        assertThrows(IllegalArgumentException.class,
                () -> new PriceBounds(null, CEM, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PriceBounds(CEM, null, false));
    }

    @Test
    void deveRejeitarPisoMaiorQueOTeto() {
        assertThrows(IllegalArgumentException.class,
                () -> new PriceBounds(CENTO_E_VINTE, CEM, false));
    }
}
