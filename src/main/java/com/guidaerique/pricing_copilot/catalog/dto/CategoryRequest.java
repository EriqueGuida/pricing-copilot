package com.guidaerique.pricing_copilot.catalog.dto;

import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CategoryRequest(
        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
        String name,

        @NotNull(message = "A vertical de negócio é obrigatória")
        Vertical vertical,

        @NotNull(message = "A margem mínima é obrigatória")
        @DecimalMin(value = "0.0001", message = "A margem mínima deve ser maior que zero")
        @DecimalMax(value = "0.9999", message = "A margem mínima deve ser menor que 1.0")
        BigDecimal minMargin,

        @NotNull(message = "A margem máxima é obrigatória")
        @DecimalMin(value = "0.0001", message = "A margem máxima deve ser maior que zero")
        @DecimalMax(value = "0.9999", message = "A margem máxima deve ser menor que 1.0")
        BigDecimal maxMargin,

        @NotNull(message = "A variação máxima é obrigatória")
        @DecimalMin(value = "0.0001", message = "A variação deve ser maior que zero")
        @DecimalMax(value = "0.5000", message = "A variação não pode exceder 0.5000 (50%)")
        BigDecimal maxVariation
) {}