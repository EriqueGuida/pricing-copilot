package com.guidaerique.pricing_copilot.pricing.kpi.service;

import com.guidaerique.pricing_copilot.pricing.kpi.dto.KpiDashboardResponse;
import com.guidaerique.pricing_copilot.pricing.kpi.repository.KpiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class KpiService {

    private final KpiRepository kpiRepository;

    public KpiService(KpiRepository kpiRepository) {
        this.kpiRepository = kpiRepository;
    }

    @Transactional(readOnly = true)
    public KpiDashboardResponse getDashboardKpis() {
        var categoryMargins = kpiRepository.findAverageMarginByCategory().stream()
            .map(p -> new KpiDashboardResponse.CategoryMarginKpi(
                p.getCategoryId(),
                p.getCategoryName(),
                p.getAvgMargin() != null ? p.getAvgMargin() : BigDecimal.ZERO
            ))
            .toList();

        long productsNearMarginFloor = kpiRepository.countProductsNearMarginFloor();

        BigDecimal aiSuccessRate = kpiRepository.calculateAiSuccessRate();
        if (aiSuccessRate == null) {
            aiSuccessRate = BigDecimal.ZERO;
        }

        var originDistribution = kpiRepository.findPriceChangesDistributionByOrigin().stream()
            .map(p -> new KpiDashboardResponse.OriginDistributionKpi(
                p.getOrigin(),
                p.getTotalCount(),
                p.getPercentage() != null ? p.getPercentage() : BigDecimal.ZERO
            ))
            .toList();

        return new KpiDashboardResponse(
            categoryMargins,
            productsNearMarginFloor,
            aiSuccessRate,
            originDistribution
        );
    }
}