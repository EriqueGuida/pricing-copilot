package com.guidaerique.pricing_copilot.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "processed_invoice",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_invoice_access_key", columnNames = {"access_key"})
    }
)
public class ProcessedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_key", nullable = false, length = 44, updatable = false)
    private String accessKey;

    @Column(name = "issuer_cnpj", nullable = false, length = 14)
    private String issuerCnpj;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "items_count", nullable = false)
    private Integer itemsCount;

    @Column(name = "recalculated_items_count", nullable = false)
    private Integer recalculatedItemsCount;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    protected ProcessedInvoice() {
    }

    public ProcessedInvoice(String accessKey, String issuerCnpj, BigDecimal totalAmount, 
                            Integer itemsCount, Integer recalculatedItemsCount) {
        this.accessKey = accessKey;
        this.issuerCnpj = issuerCnpj;
        this.totalAmount = totalAmount;
        this.itemsCount = itemsCount;
        this.recalculatedItemsCount = recalculatedItemsCount;
        this.importedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getAccessKey() { return accessKey; }
    public String getIssuerCnpj() { return issuerCnpj; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Integer getItemsCount() { return itemsCount; }
    public Integer getRecalculatedItemsCount() { return recalculatedItemsCount; }
    public LocalDateTime getImportedAt() { return importedAt; }
}