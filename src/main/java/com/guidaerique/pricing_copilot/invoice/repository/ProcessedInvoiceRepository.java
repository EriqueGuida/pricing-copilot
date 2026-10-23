package com.guidaerique.pricing_copilot.invoice.repository;

import com.guidaerique.pricing_copilot.invoice.domain.ProcessedInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedInvoiceRepository extends JpaRepository<ProcessedInvoice, Long> {
    boolean existsByAccessKey(String accessKey);
}