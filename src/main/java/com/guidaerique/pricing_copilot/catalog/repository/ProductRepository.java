package com.guidaerique.pricing_copilot.catalog.repository;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsByEan(String ean);

    Optional<Product> findBySku(String sku);

    Optional<Product> findByEan(String ean);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = true
          AND (p.manualLockUntil IS NULL OR p.manualLockUntil < :now)
    """)
    List<Product> findAllEligibleForPricing(@Param("now") LocalDateTime now);
}