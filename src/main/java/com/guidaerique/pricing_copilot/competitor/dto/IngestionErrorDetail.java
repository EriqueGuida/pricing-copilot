package com.guidaerique.pricing_copilot.competitor.dto;

public record IngestionErrorDetail(
        int line,
        String identifier,
        String message
) {}