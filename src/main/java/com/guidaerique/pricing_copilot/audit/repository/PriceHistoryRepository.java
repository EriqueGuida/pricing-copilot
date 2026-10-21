package com.guidaerique.pricing_copilot.audit.repository;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    Page<PriceHistory> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
}