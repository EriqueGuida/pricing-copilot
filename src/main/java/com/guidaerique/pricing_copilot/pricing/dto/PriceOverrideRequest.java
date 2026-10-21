package com.guidaerique.pricing_copilot.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PriceOverrideRequest(
        @NotNull(message = "O novo preço é obrigatório")
        @DecimalMin(value = "0.01", message = "O novo preço deve ser estritamente positivo")
        @Digits(integer = 10, fraction = 2, message = "O preço deve possuir no máximo 2 casas decimais")
        BigDecimal newPrice,

        @Size(max = 255, message = "A justificativa deve possuir no máximo 255 caracteres")
        String reason,

        @Min(value = 1, message = "O período de trava mínimo é 1 hora")
        @Max(value = 720, message = "O período de trava máximo é 720 horas (30 dias)")
        Integer lockHours
) {}