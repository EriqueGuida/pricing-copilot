package com.guidaerique.pricing_copilot.competitor.repository;

import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitorPriceRepository extends JpaRepository<CompetitorPrice, Long> {

    List<CompetitorPrice> findByProductIdOrderByCollectedAtDesc(Long productId);

    List<CompetitorPrice> findByProduct_IdOrderByCollectedAtDesc(Long productId);
}