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

    interface ProductPriceProjection {
        Long getProductId();
        BigDecimal getPrice();
    }

    List<CompetitorPrice> findByProductIdOrderByCollectedAtDesc(Long productId);

    @Query(value = """
        SELECT cp.price 
        FROM competitor_price cp 
        WHERE cp.product_id = :productId 
        ORDER BY cp.collected_at DESC 
        LIMIT 5
    """, nativeQuery = true)
    List<BigDecimal> findTop5LatestPrices(@Param("productId") Long productId);

    @Query(value = """
        SELECT ranked.product_id AS productId, ranked.price AS price
        FROM (
            SELECT cp.product_id, cp.price,
                   ROW_NUMBER() OVER (PARTITION BY cp.product_id ORDER BY cp.collected_at DESC) AS rn
            FROM competitor_price cp
            WHERE cp.product_id IN (:productIds)
        ) ranked
        WHERE ranked.rn <= 5
        ORDER BY ranked.product_id, ranked.rn
    """, nativeQuery = true)
    List<ProductPriceProjection> findTop5LatestPricesByProductIds(@Param("productIds") List<Long> productIds);
}