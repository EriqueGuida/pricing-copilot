package com.guidaerique.pricing_copilot.pricing.domain.event;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;

import java.math.BigDecimal;

public record PriceChangedEvent(
        Product product,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal costPrice,
        BigDecimal minMargin,
        BigDecimal maxMargin,
        BigDecimal pisoMargem,
        BigDecimal tetoMargem,
        PriceBounds bounds,
        boolean manualOverride
) {}