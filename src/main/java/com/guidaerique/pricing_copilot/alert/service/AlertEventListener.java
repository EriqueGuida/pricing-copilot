package com.guidaerique.pricing_copilot.alert.service;

import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.pricing.domain.event.PriceChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEventListener.class);
    private final AlertRepository alertRepository;

    public AlertEventListener(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @EventListener
    public void handlePriceChanged(PriceChangedEvent event) {
        // Alerta 1: Quebra de margem mínima
        if (event.newPrice().compareTo(event.pisoMargem()) < 0) {
            String message = String.format(
                    "Violação de margem mínima no produto '%s' (SKU: %s). Preço aplicado: %s, Piso da categoria: %s",
                    event.product().getName(), event.product().getSku(), event.newPrice(), event.pisoMargem()
            );
            log.warn("[ALERTA] MARGIN_BREACH disparado: {}", message);
            alertRepository.save(new Alert(event.product(), AlertType.MARGIN_BREACH, message));
        }

        // Alerta 2: Limite de variação atingido
        if (event.bounds() != null && event.bounds().variationLimitHit()) {
            String message = String.format(
                    "Limite de volatilidade/variação atingido para o produto '%s' (SKU: %s). Preço colapsado: %s",
                    event.product().getName(), event.product().getSku(), event.newPrice()
            );
            log.warn("[ALERTA] VARIATION_LIMIT_HIT disparado: {}", message);
            alertRepository.save(new Alert(event.product(), AlertType.VARIATION_LIMIT_HIT, message));
        }
    }
}