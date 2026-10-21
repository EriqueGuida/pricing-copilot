package com.guidaerique.pricing_copilot.competitor.strategy;

import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPriceSourceType;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorIngestionResponse;

public interface CompetitorPriceSource<T> {
    CompetitorPriceSourceType getSourceType();
    CompetitorIngestionResponse ingest(T source);
}