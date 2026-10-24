package com.guidaerique.pricing_copilot.pricing.kpi.controller;

import com.guidaerique.pricing_copilot.pricing.kpi.dto.KpiDashboardResponse;
import com.guidaerique.pricing_copilot.pricing.kpi.service.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kpis")
@Tag(name = "Indicadores (KPIs)", description = "Métricas executivas de margem por categoria, proximidade do piso e taxa de sucesso da IA")
@SecurityRequirement(name = "bearerAuth")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    @Operation(summary = "Obtém dashboard consolidado de KPIs de precificação e governança")
    public ResponseEntity<KpiDashboardResponse> getKpis() {
        return ResponseEntity.ok(kpiService.getDashboardKpis());
    }
}