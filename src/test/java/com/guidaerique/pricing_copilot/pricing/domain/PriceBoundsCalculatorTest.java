
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

    // ---------------------------------------------------------------
    // Janela de margem inviável em centavos (ADR-001)
    // ---------------------------------------------------------------

    @Test
    void devePriorizarMargemMinimaQuandoJanelaDeMargemForMenorQueUmCentavo() {
        // 4,20 / (1 - 0,0936) = 4,63371... -> piso (CEILING)  = 4,64
        // 4,20 / (1 - 0,0937) = 4,63422... -> teto (FLOOR)    = 4,63
        // Nenhum preço em centavos satisfaz as duas margens; a mínima prevalece.
        BigDecimal custo = bd("4.20");
        BigDecimal margemMinima = bd("0.0936");

        PriceBounds bounds = calculator.calculate(
                custo, bd("21872.74"), margemMinima, bd("0.0937"), bd("0.4241")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("4.64")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("4.64")));
        assertTrue(
                bounds.minPrice().multiply(BigDecimal.ONE.subtract(margemMinima))
                        .compareTo(custo) >= 0,
                "A margem mínima deve ser respeitada: preço * (1 - margem) >= custo."
        );
        assertTrue(
                bounds.variationLimitHit(),
                "4,64 é inalcançável a partir de 21.872,74 com variação de 42,41%."
        );
    }

    @Test
    void naoDeveSinalizarVariacaoQuandoJanelaSubcentavoENaoHaCulpaDaVariacao() {
        // Mesma janela inviável, mas o alvo (4,64) está ao alcance da variação.
        PriceBounds bounds = calculator.calculate(
                bd("4.20"), bd("4.64"), bd("0.0936"), bd("0.0937"), bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("4.64")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("4.64")));
        assertFalse(
                bounds.variationLimitHit(),
                "A causa é o arredondamento da margem, não o limite de variação."
        );
    }

    @Test
    void devePreservarJanelaDeMargemEstreitaMasViavel() {
        // 10 / 0,70 = 14,2857 -> 14,29 ; 10 / 0,69 = 14,4927 -> 14,49 (janela de 20 centavos)
        PriceBounds bounds = calculator.calculate(
                bd("10.00"), bd("14.40"), bd("0.30"), bd("0.31"), bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("14.29")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("14.49")));
        assertFalse(bounds.variationLimitHit());
    }

    // ---------------------------------------------------------------
    // Interseção entre margem e variação
    // ---------------------------------------------------------------

    @Test
    void deveManterIntersecaoParcialQuandoPrecoAtualEstaAbaixoDaMargem() {
        // Margem: [125,00 ; 166,66]. Variação de 10% sobre 120,00: [108,00 ; 132,00].
        PriceBounds bounds = calculator.calculate(
                bd("100.00"), bd("120.00"), bd("0.20"), bd("0.40"), bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("125.00")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("132.00")));
        assertFalse(
                bounds.variationLimitHit(),
                "Interseção parcial não é colapso: não deve sinalizar variação."
        );
    }

    @Test
    void deveContarPrecoAtualQuandoJaRespeitaMargemEVariacao() {
        // Margem: [125,00 ; 166,66]. Variação de 10% sobre 140,00: [126,00 ; 154,00].
        PriceBounds bounds = calculator.calculate(
                bd("100.00"), bd("140.00"), bd("0.20"), bd("0.40"), bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("126.00")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("154.00")));
        assertFalse(bounds.variationLimitHit());
    }

    @Test
    void deveAceitarPrecoAtualExatamenteNoPisoDeMargem() {
        // Piso de margem = 125,00 = preço atual. Variação: [112,50 ; 137,50].
        PriceBounds bounds = calculator.calculate(
                bd("100.00"), bd("125.00"), bd("0.20"), bd("0.40"), bd("0.10")
        );

        assertEquals(0, bounds.minPrice().compareTo(bd("125.00")));
        assertEquals(0, bounds.maxPrice().compareTo(bd("137.50")));
        assertFalse(bounds.variationLimitHit());
    }

    // ---------------------------------------------------------------
    // Validação de entradas
    // ---------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            // custo, preço atual
            "0.00, 10.00",
            "-1.00, 10.00",
            "10.00, 0.00",
            "10.00, -1.00"
    })
    void deveRejeitarCustoEPrecoAtualNaoPositivos(
            String custo,
            String precoAtual
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        bd(custo), bd(precoAtual),
                        bd("0.20"), bd("0.40"), bd("0.10")
                )
        );
    }

    @Test
    void deveRejeitarArgumentosNulos() {
        BigDecimal v = bd("0.10");
        BigDecimal p = bd("10.00");

        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, p, bd("0.20"), bd("0.40"), v));
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(p, null, bd("0.20"), bd("0.40"), v));
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(p, p, null, bd("0.40"), v));
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(p, p, bd("0.20"), null, v));
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(p, p, bd("0.20"), bd("0.40"), null));
    }
}
