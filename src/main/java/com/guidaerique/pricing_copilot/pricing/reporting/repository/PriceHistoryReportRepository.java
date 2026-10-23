package com.guidaerique.pricing_copilot.pricing.reporting.repository;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.stream.Stream;

@Repository
public interface PriceHistoryReportRepository extends JpaRepository<PriceHistory, Long> {

    @QueryHints(value = {
        @QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "500"),
        @QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "false"),
        @QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true")
    })
    @Query("""
        SELECT ph FROM PriceHistory ph
        JOIN FETCH ph.product p
        WHERE (:from IS NULL OR ph.createdAt >= :from)
          AND (:to IS NULL OR ph.createdAt <= :to)
        ORDER BY ph.createdAt DESC
    """)
    Stream<PriceHistory> streamByPeriod(@Param("from") LocalDateTime from, 
                                       @Param("to") LocalDateTime to);
}