package com.guidaerique.pricing_copilot.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank(message = "O SKU é obrigatório")
        @Size(max = 40, message = "O SKU deve conter no máximo 40 caracteres")
        String sku,

        @Pattern(regexp = "^[0-9]{13}$", message = "O EAN deve conter exatamente 13 dígitos numéricos", groups = {})
        String ean,

        @NotBlank(message = "O nome do produto é obrigatório")
        @Size(max = 160, message = "O nome deve conter no máximo 160 caracteres")
        String name,

        @NotNull(message = "O identificador da categoria é obrigatório")
        Long categoryId,

        @NotNull(message = "O preço de custo é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço de custo deve ser positivo")
        @Digits(integer = 10, fraction = 2, message = "O custo deve conter no máximo 2 casas decimais")
        BigDecimal costPrice,

        @NotNull(message = "O preço de venda atual é obrigatório")
        @DecimalMin(value = "0.01", message = "O preço de venda deve ser positivo")
        @Digits(integer = 10, fraction = 2, message = "O preço deve conter no máximo 2 casas decimais")
        BigDecimal currentPrice
) {}