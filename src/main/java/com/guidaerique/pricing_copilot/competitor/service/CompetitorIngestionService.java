package com.guidaerique.pricing_copilot.competitor.service;

import com.guidaerique.pricing_copilot.competitor.dto.CompetitorIngestionResponse;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorPriceItemRequest;
import com.guidaerique.pricing_copilot.competitor.strategy.CsvCompetitorPriceSource;
import com.guidaerique.pricing_copilot.competitor.strategy.JsonCompetitorPriceSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class CompetitorIngestionService {

    private final CsvCompetitorPriceSource csvCompetitorPriceSource;
    private final JsonCompetitorPriceSource jsonCompetitorPriceSource;

    public CompetitorIngestionService(
            CsvCompetitorPriceSource csvCompetitorPriceSource,
            JsonCompetitorPriceSource jsonCompetitorPriceSource
    ) {
        this.csvCompetitorPriceSource = csvCompetitorPriceSource;
        this.jsonCompetitorPriceSource = jsonCompetitorPriceSource;
    }

    public CompetitorIngestionResponse ingestCsv(MultipartFile file) {
        return csvCompetitorPriceSource.ingest(file);
    }

    public CompetitorIngestionResponse ingestJson(List<CompetitorPriceItemRequest> items) {
        return jsonCompetitorPriceSource.ingest(items);
    }
}