package com.guidaerique.pricing_copilot.invoice.controller;

import com.guidaerique.pricing_copilot.invoice.dto.InvoiceImportResponse;
import com.guidaerique.pricing_copilot.invoice.service.NfeCostUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Notas Fiscais (NF-e)", description = "Importação de XML de NF-e com atualização de custos e proteção automática de piso de margem")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final NfeCostUpdateService invoiceService;

    public InvoiceController(NfeCostUpdateService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Importa XML de NF-e, atualiza custos de catálogo e recalcula piso de margem")
    public ResponseEntity<InvoiceImportResponse> importNfeInvoice(@RequestParam("file") MultipartFile file) {
        InvoiceImportResponse response = invoiceService.processInvoiceXml(file);
        return ResponseEntity.ok(response);
    }
}