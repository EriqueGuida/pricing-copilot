package com.guidaerique.pricing_copilot.pricing.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_run")
public class PricingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PricingRunStatus status;

    @Column(name = "processed_count", nullable = false)
    private int processedItems = 0;

    @Column(name = "changed_count", nullable = false)
    private int updatedItems = 0;

    @Column(name = "skipped_count", nullable = false)
    private int skippedItems = 0;

    @Column(name = "error_count", nullable = false)
    private int errorItems = 0;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = PricingRunStatus.RUNNING;
        }
    }

    public PricingRun() {
    }

    public void incrementProcessed() {
        this.processedItems++;
    }

    public void incrementUpdated() {
        this.updatedItems++;
    }

    public void incrementSkipped() {
        this.skippedItems++;
    }

    public void incrementError() {
        this.errorItems++;
    }

    public void complete(String modelVersion) {
        this.finishedAt = LocalDateTime.now();
        this.modelVersion = modelVersion;
        if (this.errorItems == 0) {
            this.status = PricingRunStatus.SUCCESS;
        } else if (this.updatedItems > 0 || this.skippedItems > 0) {
            this.status = PricingRunStatus.PARTIAL;
        } else {
            this.status = PricingRunStatus.FAILED;
        }
    }

    public void fail(String modelVersion) {
        this.finishedAt = LocalDateTime.now();
        this.modelVersion = modelVersion;
        this.status = PricingRunStatus.FAILED;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public PricingRunStatus getStatus() { return status; }
    public int getProcessedItems() { return processedItems; }
    public int getUpdatedItems() { return updatedItems; }
    public int getSkippedItems() { return skippedItems; }
    public int getErrorItems() { return errorItems; }
    public String getModelVersion() { return modelVersion; }
}