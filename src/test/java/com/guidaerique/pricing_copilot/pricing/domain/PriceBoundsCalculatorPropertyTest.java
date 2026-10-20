package com.guidaerique.pricing_copilot.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de propriedade com seed fixa (reproduzível).
 *
 * Compara o {@link PriceBoundsCalculator} (BigDecimal) com um oráculo
 * independente em ARITMÉTICA INTEIRA DE CENTAVOS. Com margens e variação em
 * décimos de milésimo (escala 10^4), as desigualdades viram:
 *
 *   margem(p) >= m   <=>   p * (10^4 - m) >= custo * 10^4
 *
 * Logo o menor preço em centavos que respeita a margem mínima é
 * ceil(custo * 10^4 / (10^4 - mínima)) e o maior que respeita a máxima é
 * floor(custo * 10^4 / (10^4 - máxima)). Sem tolerâncias e sem ponto flutuante.
 *
 * Os invariantes dependem do cenário: a margem MÁXIMA só é exigida quando
 * existe ao menos um preço em centavos dentro da janela de margem.
 */
class PriceBoundsCalculatorPropertyTest {

    private static final long SEED = 42L;
    private static final int AMOSTRAS = 300_000;
    private static final long ESCALA = 10_000L;

    private final PriceBoundsCalculator calculator =
            new PriceBoundsCalculator();

    @Test
    void deveRespeitarInvariantesDeMargemEVariacaoEmCentavos() {
        Random random = new Random(SEED);
        int janelasInviaveis = 0;
        int janelasViaveis = 0;
        int intersecoesVazias = 0;
        int intersecoesVaziasPorVariacao = 0;

        for (int i = 0; i < AMOSTRAS; i++) {
            // i % 3 == 0 gera janelas de margem estreitas e custos baixos,
            // onde o arredondamento a centavos pode tornar a janela inviável.
            boolean estreita = i % 3 == 0;

            long custo = estreita
                    ? 1 + random.nextInt(5_000)
                    : 1 + random.nextInt(2_000_000);
            long margemMinima = 1 + random.nextInt(9_000);
            long margemMaxima = estreita
                    ? margemMinima + 1 + random.nextInt(3)
                    : margemMinima + 1
                        + random.nextInt((int) (9_999 - margemMinima));
            long variacao = 1 + random.nextInt(5_000);

            long pisoMargem = ceilDiv(custo * ESCALA, ESCALA - margemMinima);
            long tetoMargem = floorDiv(custo * ESCALA, ESCALA - margemMaxima);
            long atual = sortearPrecoAtual(
                    random, i, pisoMargem, tetoMargem
            );

            long pisoVariacao = ceilDiv(atual * (ESCALA - variacao), ESCALA);
            long tetoVariacao = floorDiv(atual * (ESCALA + variacao), ESCALA);
            long inicio = Math.max(pisoMargem, pisoVariacao);
            long fim = Math.min(tetoMargem, tetoVariacao);
            boolean janelaViavel = pisoMargem <= tetoMargem;

            BigDecimal custoBd = BigDecimal.valueOf(custo, 2);
            BigDecimal minimaBd = BigDecimal.valueOf(margemMinima, 4);
            BigDecimal maximaBd = BigDecimal.valueOf(margemMaxima, 4);

            PriceBounds bounds = calculator.calculate(
                    custoBd,
                    BigDecimal.valueOf(atual, 2),
                    minimaBd,
                    maximaBd,
                    BigDecimal.valueOf(variacao, 4)
            );

            // longValueExact falha se o preço tiver mais de 2 casas.
            long min = bounds.minPrice().movePointRight(2).longValueExact();
            long max = bounds.maxPrice().movePointRight(2).longValueExact();
            String ctx = "custo=" + custoBd + " atual="
                    + BigDecimal.valueOf(atual, 2) + " min=" + minimaBd
                    + " max=" + maximaBd + " var=" + variacao
                    + "/10000 -> [" + bounds.minPrice() + ", "
                    + bounds.maxPrice() + "] hit="
                    + bounds.variationLimitHit();

            // Invariante A: a margem mínima NUNCA é violada (exato, sem divisão).
            assertTrue(
                    bounds.minPrice()
                            .multiply(BigDecimal.ONE.subtract(minimaBd))
                            .compareTo(custoBd) >= 0,
                    () -> "Margem mínima violada: " + ctx
            );

            if (janelaViavel) {
                janelasViaveis++;

                // Invariante B: com janela viável, a margem máxima também vale.
                assertTrue(
                        bounds.maxPrice()
                                .multiply(BigDecimal.ONE.subtract(maximaBd))
                                .compareTo(custoBd) <= 0,
                        () -> "Margem máxima violada: " + ctx
                );
            } else {
                janelasInviaveis++;
            }

            if (inicio <= fim) {
                // Invariante C: interseção não vazia = interseção exata.
                assertEquals(inicio, min, () -> "Piso incorreto: " + ctx);
                assertEquals(fim, max, () -> "Teto incorreto: " + ctx);
                assertFalse(
                        bounds.variationLimitHit(),
                        () -> "Sinalizou variação sem colapso: " + ctx
                );
            } else {
                intersecoesVazias++;

                // Invariante D: colapso no limite de margem; a mínima
                // prevalece se a janela for inviável em centavos.
                long alvo = (!janelaViavel || atual < pisoMargem)
                        ? pisoMargem
                        : tetoMargem;
                assertEquals(alvo, min, () -> "Alvo incorreto (piso): " + ctx);
                assertEquals(alvo, max, () -> "Alvo incorreto (teto): " + ctx);

                // Invariante E: só sinaliza variação se ela impede o alvo.
                boolean variacaoImpede =
                        alvo < pisoVariacao || alvo > tetoVariacao;
                if (variacaoImpede) {
                    intersecoesVaziasPorVariacao++;
                }
                assertEquals(
                        variacaoImpede,
                        bounds.variationLimitHit(),
                        () -> "Sinalização de variação incorreta: " + ctx
                );
            }

            // Invariante F: preço atual já dentro da margem cabe no intervalo.
            if (janelaViavel && atual >= pisoMargem && atual <= tetoMargem) {
                assertTrue(
                        min <= atual && atual <= max,
                        () -> "Preço atual válido ficou de fora: " + ctx
                );
                assertFalse(
                        bounds.variationLimitHit(),
                        () -> "Preço atual válido não deveria colapsar: " + ctx
                );
            }
        }

        // Garante que o teste não é vazio: todos os cenários foram exercitados.
        assertTrue(janelasViaveis > 0, "Nenhuma janela viável sorteada.");
        assertTrue(janelasInviaveis > 0, "Nenhuma janela subcentavo sorteada.");
        assertTrue(intersecoesVazias > 0, "Nenhuma interseção vazia sorteada.");
        assertTrue(
                intersecoesVaziasPorVariacao > 0,
                "Nenhum colapso causado pela variação sorteado."
        );
    }

    private static long sortearPrecoAtual(
            Random random, int i, long pisoMargem, long tetoMargem
    ) {
        switch (i % 4) {
            case 1:
                // Dentro da janela de margem, quando ela existe.
                if (pisoMargem <= tetoMargem) {
                    long largura = tetoMargem - pisoMargem + 1;
                    return pisoMargem
                            + Math.floorMod(random.nextLong(), largura);
                }
                return Math.max(1, pisoMargem);
            case 2:
                return perto(random, pisoMargem);
            case 3:
                return perto(random, tetoMargem);
            default:
                return 1 + random.nextInt(3_000_000);
        }
    }

    /** Sorteia um preço em torno de {@code base} (±1/3), sempre positivo. */
    private static long perto(Random random, long base) {
        long folga = Math.max(1, base / 3);
        return Math.max(
                1,
                base - folga + Math.floorMod(random.nextLong(), 2 * folga + 1)
        );
    }

    // Math.ceilDiv só existe a partir do Java 18; o projeto compila em Java 17.
    private static long ceilDiv(long a, long b) {
        return -Math.floorDiv(-a, b);
    }

    private static long floorDiv(long a, long b) {
        return Math.floorDiv(a, b);
    }
}
