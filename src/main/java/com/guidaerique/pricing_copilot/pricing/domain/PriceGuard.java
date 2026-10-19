package com.guidaerique.pricing_copilot.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class PriceGuard {

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