package com.guidaerique.pricing_copilot.competitor.repository;

import com.guidaerique.pricing_copilot.competitor.domain.Competitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompetitorRepository extends JpaRepository<Competitor, Long> {
    Optional<Competitor> findByName(String name);
    Optional<Competitor> findByNameIgnoreCase(String name);
    boolean existsByName(String name);
}