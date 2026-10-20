package com.guidaerique.pricing_copilot.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class PriceGuard {

    private static final int MONEY_SCALE = 2;

    public void validate(
            BigDecimal suggestedPrice,
            PriceBounds bounds
    ) {
        Objects.requireNonNull(
                suggestedPrice, "Preço sugerido é obrigatório."
        );
        Objects.requireNonNull(bounds, "Limites são obrigatórios.");

        if (suggestedPrice.signum() <= 0) {
            throw new InvalidPriceSuggestionException(
                    "O preço sugerido deve ser positivo."
            );
        }

        // O banco grava DECIMAL(12,2): um preço com mais de 2 casas
        // significativas seria arredondado na persistência, e o valor
        // gravado divergiria do validado e auditado. stripTrailingZeros
        // aceita 110.0 / 110.00 / 110.000, mas rejeita 110.005.
        if (suggestedPrice.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new InvalidPriceSuggestionException(
                    "O preço sugerido deve ter no máximo "
                            + MONEY_SCALE + " casas decimais."
            );
        }

        if (suggestedPrice.compareTo(bounds.minPrice()) < 0
                || suggestedPrice.compareTo(bounds.maxPrice()) > 0) {
            throw new InvalidPriceSuggestionException(
                    "Preço sugerido fora do intervalo permitido: ["
                            + bounds.minPrice() + ", "
                            + bounds.maxPrice() + "]."
            );
        }
    }
}