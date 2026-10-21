package com.guidaerique.pricing_copilot.competitor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompetitorPriceItemRequest(
        @Size(max = 40, message = "O SKU deve conter no máximo 40 caracteres")
        String sku,

        @Size(max = 13, message = "O EAN deve conter no máximo 13 caracteres")
        String ean,

        @NotBlank(message = "O nome do concorrente é obrigatório")
        @Size(max = 100, message = "O nome do concorrente deve conter no máximo 100 caracteres")
        String competitorName,

        @NotNull(message = "O preço praticado é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço praticado deve ser estritamente maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O preço deve conter no máximo 2 casas decimais")
        BigDecimal price,

        LocalDateTime collectedAt
) {}