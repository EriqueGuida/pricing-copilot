package com.guidaerique.pricing_copilot.pricing.kpi.controller;

import com.guidaerique.pricing_copilot.pricing.kpi.dto.KpiDashboardResponse;
import com.guidaerique.pricing_copilot.pricing.kpi.service.KpiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kpis")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    public ResponseEntity<KpiDashboardResponse> getKpis() {
        return ResponseEntity.ok(kpiService.getDashboardKpis());
    }
}