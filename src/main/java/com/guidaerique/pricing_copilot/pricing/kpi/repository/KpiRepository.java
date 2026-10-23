package com.guidaerique.pricing_copilot.pricing.kpi.repository;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface KpiRepository extends JpaRepository<PriceHistory, Long> {

    interface CategoryMarginProjection {
        Long getCategoryId();
        String getCategoryName();
        BigDecimal getAvgMargin();
    }

    interface OriginDistributionProjection {
        String getOrigin();
        Long getTotalCount();
        BigDecimal getPercentage();
    }

    @Query(value = """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               ROUND(AVG(((p.current_price - p.cost_price) / p.current_price) * 100), 2) AS avgMargin
        FROM product p
        JOIN category c ON p.category_id = c.id
        WHERE p.current_price > 0
        GROUP BY c.id, c.name
    """, nativeQuery = true)
    List<CategoryMarginProjection> findAverageMarginByCategory();

    @Query(value = """
        SELECT COUNT(p.id)
        FROM product p
        JOIN category c ON p.category_id = c.id
        WHERE p.current_price > 0
          AND ((p.current_price - p.cost_price) / p.current_price) >= c.min_margin
          AND ((p.current_price - p.cost_price) / p.current_price) <= (c.min_margin + 0.01)
    """, nativeQuery = true)
    long countProductsNearMarginFloor();

    @Query(value = """
        SELECT ROUND(
            COALESCE(
                (SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) * 100.0) / NULLIF(COUNT(id), 0),
                0.0
            ), 2)
        FROM pricing_run
    """, nativeQuery = true)
    BigDecimal calculateAiSuccessRate();

    @Query(value = """
        SELECT ph.origin AS origin,
               COUNT(ph.id) AS totalCount,
               ROUND((COUNT(ph.id) * 100.0) / (SELECT NULLIF(COUNT(*), 0) FROM price_history), 2) AS percentage
        FROM price_history ph
        GROUP BY ph.origin
    """, nativeQuery = true)
    List<OriginDistributionProjection> findPriceChangesDistributionByOrigin();
}