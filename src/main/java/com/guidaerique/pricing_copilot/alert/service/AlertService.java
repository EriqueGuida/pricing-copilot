package com.guidaerique.pricing_copilot.alert.service;

import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertStatus;
import com.guidaerique.pricing_copilot.alert.dto.AlertResponse;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(AlertStatus status, Pageable pageable) {
        if (status != null) {
            return alertRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(AlertResponse::fromEntity);
        }
        return alertRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AlertResponse::fromEntity);
    }

    @Transactional
    public AlertResponse resolveAlert(Long alertId, AppUser currentUser) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado com o ID: " + alertId));

        alert.resolve(currentUser);
        return AlertResponse.fromEntity(alert);
    }
}