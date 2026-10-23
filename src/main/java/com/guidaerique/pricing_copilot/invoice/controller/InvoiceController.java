package com.guidaerique.pricing_copilot.invoice.controller;

import com.guidaerique.pricing_copilot.invoice.dto.InvoiceImportResponse;
import com.guidaerique.pricing_copilot.invoice.service.NfeCostUpdateService;
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
public class InvoiceController {

    private final NfeCostUpdateService invoiceService;

    public InvoiceController(NfeCostUpdateService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<InvoiceImportResponse> importNfeInvoice(@RequestParam("file") MultipartFile file) {
        InvoiceImportResponse response = invoiceService.processInvoiceXml(file);
        return ResponseEntity.ok(response);
    }
}