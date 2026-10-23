package com.guidaerique.pricing_copilot.competitor.repository;

import com.guidaerique.pricing_copilot.competitor.domain.CompetitorPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CompetitorPriceRepository extends JpaRepository<CompetitorPrice, Long> {

    List<CompetitorPrice> findByProductIdOrderByCollectedAtDesc(Long productId);

    @Query(value = """
        SELECT cp.price 
        FROM competitor_price cp 
        WHERE cp.product_id = :productId 
        ORDER BY cp.collected_at DESC 
        LIMIT 5
    """, nativeQuery = true)
    List<BigDecimal> findTop5LatestPrices(@Param("productId") Long productId);
}