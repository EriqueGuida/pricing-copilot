package com.guidaerique.pricing_copilot.pricing.repository;

import com.guidaerique.pricing_copilot.pricing.domain.PricingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingRunRepository extends JpaRepository<PricingRun, Long> {
}