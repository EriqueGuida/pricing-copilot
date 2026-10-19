package com.guidaerique.pricing_copilot.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class PriceBoundsCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int DIVISION_SCALE = 12;

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PriceBounds calculate(
            BigDecimal cost,
            BigDecimal currentPrice,
            BigDecimal minMargin,
            BigDecimal maxMargin,
            BigDecimal maxVariation
    ) {
        validateInputs(
                cost, currentPrice, minMargin, maxMargin, maxVariation
        );

        BigDecimal pisoMargem = cost.divide(
                ONE.subtract(minMargin),
                DIVISION_SCALE,
                RoundingMode.CEILING
        ).setScale(MONEY_SCALE, RoundingMode.CEILING);

        BigDecimal tetoMargem = cost.divide(
                ONE.subtract(maxMargin),
                DIVISION_SCALE,
                RoundingMode.FLOOR
        ).setScale(MONEY_SCALE, RoundingMode.FLOOR);

        BigDecimal pisoVariacao = currentPrice
                .multiply(ONE.subtract(maxVariation))
                .setScale(MONEY_SCALE, RoundingMode.CEILING);

        BigDecimal tetoVariacao = currentPrice
                .multiply(ONE.add(maxVariation))
                .setScale(MONEY_SCALE, RoundingMode.FLOOR);

        BigDecimal minEfetivo = pisoMargem.max(pisoVariacao);
        BigDecimal maxEfetivo = tetoMargem.min(tetoVariacao);

        if (minEfetivo.compareTo(maxEfetivo) <= 0) {
            return new PriceBounds(
                    minEfetivo, maxEfetivo, false
            );
        }

        // Interseção vazia: a margem prevalece sobre a variação.
        BigDecimal alvo = currentPrice.compareTo(pisoMargem) < 0
                ? pisoMargem
                : tetoMargem;

        return new PriceBounds(alvo, alvo, true);
    }

    private void validateInputs(
            BigDecimal cost,
            BigDecimal currentPrice,
            BigDecimal minMargin,
            BigDecimal maxMargin,
            BigDecimal maxVariation
    ) {
        Objects.requireNonNull(cost, "Custo é obrigatório.");
        Objects.requireNonNull(
                currentPrice, "Preço atual é obrigatório."
        );
        Objects.requireNonNull(
                minMargin, "Margem mínima é obrigatória."
        );
        Objects.requireNonNull(
                maxMargin, "Margem máxima é obrigatória."
        );
        Objects.requireNonNull(
                maxVariation, "Variação máxima é obrigatória."
        );

        if (cost.compareTo(ZERO) <= 0
                || currentPrice.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Custo e preço atual devem ser positivos."
            );
        }

        if (minMargin.compareTo(ZERO) <= 0
                || minMargin.compareTo(maxMargin) >= 0
                || maxMargin.compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(
                    "As margens devem satisfazer 0 < mínima < máxima < 1."
            );
        }

        if (maxVariation.compareTo(ZERO) <= 0
                || maxVariation.compareTo(
                        new BigDecimal("0.5")) > 0) {
            throw new IllegalArgumentException(
                    "A variação máxima deve estar entre 0 e 0,5."
            );
        }
    }
}