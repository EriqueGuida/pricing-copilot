package com.guidaerique.pricing_copilot.invoice.dto;

import java.math.BigDecimal;

public record InvoiceImportResponse(
    String accessKey,
    String issuerCnpj,
    BigDecimal totalInvoiceAmount,
    int itemsProcessed,
    int pricesAdjustedToFloor,
    String message
) {}