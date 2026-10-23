package com.guidaerique.pricing_copilot.pricing.kpi.dto;

import java.math.BigDecimal;
import java.util.List;

public record KpiDashboardResponse(
    List<CategoryMarginKpi> categoryMargins,
    long productsNearMarginFloor,
    BigDecimal aiAutonomousSuccessRate,
    List<OriginDistributionKpi> priceChangesByOrigin
) {
    public record CategoryMarginKpi(
        Long categoryId,
        String categoryName,
        BigDecimal averageMarginPercent
    ) {}

    public record OriginDistributionKpi(
        String origin,
        long count,
        BigDecimal percentage
    ) {}
}