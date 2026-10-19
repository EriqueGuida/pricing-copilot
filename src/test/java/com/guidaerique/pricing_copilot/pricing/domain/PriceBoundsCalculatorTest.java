
package com.guidaerique.pricing_copilot.pricing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceBoundsCalculatorTest {

    private final PriceBoundsCalculator calculator =
            new PriceBoundsCalculator();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @ParameterizedTest
    @CsvSource({
            // custo, margem mínima, preço atual, piso esperado
            "10.00, 0.30, 14.00, 14.29",
            "80.00, 0.20, 100.00, 100.00",
            "1.00, 0.10, 1.10, 1.12"
    })
    void deveArredondarPisoDeMargemParaCima(
            String custo,
            String margemMinima,
            String precoAtual,
            String pisoEsperado
    ) {
        PriceBounds bounds = calculator.calculate(
                bd(custo),
                bd(precoAtual),
                bd(margemMinima),
                bd("0.90"),
                bd("0.10")
        );

        assertEquals(
                0,
                bounds.minPrice().compareTo(bd(pisoEsperado)),
                "O piso de margem deve ser arredondado para cima."
        );

        assertFalse(
                bounds.variationLimitHit(),
                "A interseção deve ser válida neste cenário."
        );
    }

    @ParameterizedTest
    @CsvSource({
            // custo, margem máxima, preço atual, teto esperado
            "10.00, 0.40, 16.00, 16.66",
            "80.00, 0.40, 130.00, 133.33",
            "1.00, 0.30, 1.30, 1.42"
    })
    void deveArredondarTetoDeMargemParaBaixo(
            String custo,
            String margemMaxima,
            String precoAtual,
            String tetoEsperado
    ) {
        PriceBounds bounds = calculator.calculate(
                bd(custo),
                bd(precoAtual),
                bd("0.10"),
                bd(margemMaxima),
                bd("0.10")
        );

        assertEquals(
                0,
                bounds.maxPrice().compareTo(bd(tetoEsperado)),
                "O teto de margem deve ser arredondado para baixo."
        );

        assertFalse(
                bounds.variationLimitHit(),
                "A interseção deve ser válida neste cenário."
        );
    }

    
    
    @ParameterizedTest
    @CsvSource({
            // preço atual, piso esperado, teto esperado
            "10.00, 9.00, 11.00",
            "80.00, 72.00, 88.00",
            "1.00, 0.90, 1.10"
    })
    void deveRespeitarVariacaoMaxima(
            String precoAtual,
            String pisoEsperado,
            String tetoEsperado
    ) {
        PriceBounds bounds = calculator.calculate(
                bd("0.01"),
                bd(precoAtual),
                bd("0.001"),
                bd("0.9999"),
                bd("0.10")
        );

        assertEquals(
                0,
                bounds.minPrice().compareTo(bd(pisoEsperado)),
                "O piso deve respeitar a variação máxima."
        );

        assertEquals(
                0,
                bounds.maxPrice().compareTo(bd(tetoEsperado)),
                "O teto deve respeitar a variação máxima."
        );

        assertFalse(
                bounds.variationLimitHit(),
                "A interseção deve ser válida neste cenário."
        );
    }



    @Test
    void devePriorizarMargemQuandoIntersecaoForVazia() {
        PriceBounds bounds = calculator.calculate(
                bd("100.00"),
                bd("100.00"),
                bd("0.20"),
                bd("0.40"),
                bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("125.00")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("125.00")));

        assertTrue(
                bounds.variationLimitHit(),
                "Deve sinalizar quando a variação não comporta o limite de margem."
        );
    }

    @Test
    void deveColapsarNoTetoDeMargemQuandoPrecoAtualForAlto() {
        PriceBounds bounds = calculator.calculate(
                bd("100.00"),
                bd("200.00"),
                bd("0.20"),
                bd("0.40"),
                bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("166.66")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("166.66")));

        assertTrue(
                bounds.variationLimitHit(),
                "Deve sinalizar quando o preço atual está acima do teto de margem."
        );
    }

    @ParameterizedTest
    @CsvSource({
            // margem mínima, margem máxima, variação máxima
            "0.00, 0.40, 0.10",
            "0.20, 1.00, 0.10",
            "0.40, 0.20, 0.10",
            "0.20, 0.40, 0.00",
            "0.20, 0.40, 0.60"
    })
    void deveRejeitarLimitesInvalidos(
            String margemMinima,
            String margemMaxima,
            String variacao
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        bd("100.00"),
                        bd("150.00"),
                        bd(margemMinima),
                        bd(margemMaxima),
                        bd(variacao)
                )
        );
    }
}
