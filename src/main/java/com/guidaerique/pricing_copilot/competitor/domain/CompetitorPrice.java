package com.guidaerique.pricing_copilot.competitor.domain;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitor_price")
public class CompetitorPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competitor_id", nullable = false)
    private Competitor competitor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private LocalDateTime collectedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompetitorPriceSourceType source = CompetitorPriceSourceType.CSV;

    public CompetitorPrice() {}

    public CompetitorPrice(
            Product product,
            Competitor competitor,
            BigDecimal price,
            LocalDateTime collectedAt,
            CompetitorPriceSourceType source
    ) {
        this.product = product;
        this.competitor = competitor;
        this.price = price;
        this.collectedAt = collectedAt != null ? collectedAt : LocalDateTime.now();
        this.source = source != null ? source : CompetitorPriceSourceType.CSV;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Competitor getCompetitor() { return competitor; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public CompetitorPriceSourceType getSource() { return source; }
}