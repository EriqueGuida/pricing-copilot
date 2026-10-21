package com.guidaerique.pricing_copilot.competitor.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorIngestionResponse;
import com.guidaerique.pricing_copilot.competitor.dto.CompetitorPriceItemRequest;
import com.guidaerique.pricing_copilot.competitor.service.CompetitorIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/competitor-prices")
@Tag(name = "Concorrentes", description = "Ingestão e consulta de preços praticados por concorrentes")
@SecurityRequirement(name = "bearerAuth")
public class CompetitorPriceController {

    private final CompetitorIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public CompetitorPriceController(CompetitorIngestionService ingestionService) {
        this.ingestionService = ingestionService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Importação em massa de preços de concorrentes via arquivo CSV (Multipart)")
    @ApiResponse(responseCode = "200", description = "Relatório detalhado de execução da importação")
    public ResponseEntity<CompetitorIngestionResponse> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ingestionService.ingestCsv(file));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Ingestão direta de preços de concorrentes via JSON (individual ou lote)")
    @ApiResponse(responseCode = "200", description = "Relatório detalhado de execução da importação")
    public ResponseEntity<CompetitorIngestionResponse> importJson(
            @RequestBody(required = false) String rawPayload
    ) throws IOException {
        if (rawPayload == null || rawPayload.trim().isEmpty() || rawPayload.trim().equals("[]")) {
            return ResponseEntity.ok(new CompetitorIngestionResponse(0, 0, 0, List.of()));
        }

        JsonNode node = objectMapper.readTree(rawPayload);
        List<CompetitorPriceItemRequest> items;

        if (node.isArray()) {
            items = objectMapper.readerFor(new TypeReference<List<CompetitorPriceItemRequest>>() {})
                    .readValue(node);
        } else {
            CompetitorPriceItemRequest single = objectMapper.treeToValue(node, CompetitorPriceItemRequest.class);
            items = List.of(single);
        }

        return ResponseEntity.ok(ingestionService.ingestJson(items));
    }
}