package com.guidaerique.pricing_copilot.pricing.controller;

import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import com.guidaerique.pricing_copilot.pricing.dto.PricingRunResponse;
import com.guidaerique.pricing_copilot.pricing.repository.PricingRunRepository;
import com.guidaerique.pricing_copilot.pricing.service.PricingRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pricing/runs")
@Tag(name = "Pricing Runs", description = "Endpoints para orquestracao do ciclo de precificacao automatizado")
public class PricingRunController {

    private final PricingRunService pricingRunService;
    private final PricingRunRepository pricingRunRepository;

    public PricingRunController(PricingRunService pricingRunService,
                                PricingRunRepository pricingRunRepository) {
        this.pricingRunService = pricingRunService;
        this.pricingRunRepository = pricingRunRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Disparar ciclo de precificacao", description = "Executa a avaliacao em lote contra a IA para produtos elegiveis.")
    @ApiResponse(responseCode = "202", description = "Ciclo disparado com sucesso")
    public ResponseEntity<PricingRunResponse> triggerRun() {
        PricingRun run = pricingRunService.triggerManualRun();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PricingRunResponse.fromEntity(run));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    @Operation(summary = "Consultar status de um run", description = "Recupera metricas e totalizadores consolidados de um ciclo de precificacao.")
    public ResponseEntity<PricingRunResponse> getRunById(@PathVariable Long id) {
        return pricingRunRepository.findById(id)
                .map(run -> ResponseEntity.ok(PricingRunResponse.fromEntity(run)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pricing Run nao encontrado com ID: " + id));
    }
}