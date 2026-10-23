package com.guidaerique.pricing_copilot.pricing.reporting.controller;

import com.guidaerique.pricing_copilot.pricing.reporting.service.PriceHistoryReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final PriceHistoryReportService reportService;

    public ReportController(PriceHistoryReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping(value = "/price-history.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    public ResponseEntity<StreamingResponseBody> exportPriceHistoryCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        StreamingResponseBody stream = outputStream -> reportService.streamPriceHistoryCsv(from, to, outputStream);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"price-history.csv\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(stream);
    }
}