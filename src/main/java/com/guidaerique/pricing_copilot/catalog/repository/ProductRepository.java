package com.guidaerique.pricing_copilot.catalog.repository;

import com.guidaerique.pricing_copilot.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findByEan(String ean);
    boolean existsBySku(String sku);
    boolean existsByEan(String ean);
}