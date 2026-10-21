package com.guidaerique.pricing_copilot.alert.controller;

import com.guidaerique.pricing_copilot.alert.domain.AlertStatus;
import com.guidaerique.pricing_copilot.alert.dto.AlertResponse;
import com.guidaerique.pricing_copilot.alert.service.AlertService;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alertas", description = "Monitoramento e resolução de alertas operacionais de preço e margem")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    @Operation(summary = "Lista alertas operacionais com filtro opcional por status")
    public ResponseEntity<Page<AlertResponse>> getAlerts(
            @RequestParam(required = false) AlertStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(alertService.findAll(status, pageable));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Marca um alerta como resolvido")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ResponseEntity.ok(alertService.resolveAlert(id, currentUser));
    }
}