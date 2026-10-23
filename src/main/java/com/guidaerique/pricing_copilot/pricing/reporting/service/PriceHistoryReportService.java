package com.guidaerique.pricing_copilot.pricing.reporting.service;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.pricing.reporting.repository.PriceHistoryReportRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.stream.Stream;

@Service
public class PriceHistoryReportService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "ID,PRODUCT_ID,SKU,OLD_PRICE,NEW_PRICE,ORIGIN,REASON,CHANGED_AT\n";

    private final PriceHistoryReportRepository repository;
    private final EntityManager entityManager;

    public PriceHistoryReportService(PriceHistoryReportRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public void streamPriceHistoryCsv(LocalDate from, LocalDate to, OutputStream outputStream) {
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
             Stream<PriceHistory> historyStream = repository.streamByPeriod(fromDateTime, toDateTime)) {

            writer.write(CSV_HEADER);
            int batchCounter = 0;

            Iterator<PriceHistory> iterator = historyStream.iterator();
            while (iterator.hasNext()) {
                PriceHistory entry = iterator.next();
                writer.write(formatCsvRecord(entry));
                writer.newLine();

                entityManager.detach(entry);

                if (++batchCounter % 500 == 0) {
                    writer.flush();
                }
            }
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Erro de I/O durante streaming do arquivo CSV de histórico", ex);
        }
    }

    private String formatCsvRecord(PriceHistory h) {
        return String.format(
            "%d,%d,\"%s\",%s,%s,%s,\"%s\",\"%s\"",
            h.getId(),
            h.getProduct().getId(),
            escapeCsv(h.getProduct().getSku()),
            h.getOldPrice().toPlainString(),
            h.getNewPrice().toPlainString(),
            h.getOrigin().name(),
            escapeCsv(h.getReason()),
            h.getCreatedAt().format(ISO_DATE_TIME)
        );
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}