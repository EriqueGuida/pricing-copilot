package com.guidaerique.pricing_copilot.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vertical vertical;

    @Column(name = "min_margin", nullable = false, precision = 5, scale = 4)
    private BigDecimal minMargin;

    @Column(name = "max_margin", nullable = false, precision = 5, scale = 4)
    private BigDecimal maxMargin;

    @Column(name = "max_variation", nullable = false, precision = 5, scale = 4)
    private BigDecimal maxVariation;

    public Category() {}

    public Category(String name, Vertical vertical, BigDecimal minMargin, BigDecimal maxMargin, BigDecimal maxVariation) {
        this.name = name;
        this.vertical = vertical;
        this.minMargin = minMargin;
        this.maxMargin = maxMargin;
        this.maxVariation = maxVariation;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Vertical getVertical() { return vertical; }
    public void setVertical(Vertical vertical) { this.vertical = vertical; }
    public BigDecimal getMinMargin() { return minMargin; }
    public void setMinMargin(BigDecimal minMargin) { this.minMargin = minMargin; }
    public BigDecimal getMaxMargin() { return maxMargin; }
    public void setMaxMargin(BigDecimal maxMargin) { this.maxMargin = maxMargin; }
    public BigDecimal getMaxVariation() { return maxVariation; }
    public void setMaxVariation(BigDecimal maxVariation) { this.maxVariation = maxVariation; }
}