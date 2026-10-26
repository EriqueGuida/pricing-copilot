package com.guidaerique.pricing_copilot.pricing;

import com.guidaerique.pricing_copilot.IntegrationTest;
import com.guidaerique.pricing_copilot.alert.domain.Alert;
import com.guidaerique.pricing_copilot.alert.domain.AlertType;
import com.guidaerique.pricing_copilot.alert.repository.AlertRepository;
import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.dto.PriceOverrideRequest;
import com.guidaerique.pricing_copilot.pricing.service.PriceOverrideService;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.security.domain.Role;
import com.guidaerique.pricing_copilot.security.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PriceOverrideAuditIT {

    @Autowired
    private PriceOverrideService priceOverrideService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Product product;
    private AppUser adminUser;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category(
                "Audit-Category-" + UUID.randomUUID(),
                Vertical.CONSUMER_ELECTRONICS,
                new BigDecimal("0.2000"), // 20%
                new BigDecimal("0.5000"), // 50%
                new BigDecimal("0.1000")  // 10%
        ));

        // Custo = 100.00 -> Piso: 125.00, Teto: 200.00
        product = productRepository.save(new Product(
                "SKU-AUD-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Monitor Gamer 144Hz",
                category,
                new BigDecimal("100.00"),
                new BigDecimal("150.00")
        ));

        adminUser = userRepository.save(new AppUser(
                "admin-" + UUID.randomUUID() + "@audit.com",
                "secret",
                Role.ADMIN
        ));
    }

    @Test
    @DisplayName("Sobrescrita manual deve persistir atomicamente o histórico com origem MANUAL")
    void devePersistirHistoricoDePrecoNaMesmaTransacao() {
        BigDecimal precoNovo = new BigDecimal("170.00");
        PriceOverrideRequest request = new PriceOverrideRequest(
                precoNovo,
                "Ajuste estratégico de posicionamento de mercado",
                48
        );

        priceOverrideService.overridePrice(product.getId(), request, adminUser);

        // 1. Validação da mutação do produto
        Product produtoAtualizado = productRepository.findById(product.getId()).orElseThrow();
        assertThat(produtoAtualizado.getCurrentPrice()).isEqualByComparingTo(precoNovo);
        assertThat(produtoAtualizado.getManualLockUntil()).isAfter(LocalDateTime.now().plusHours(40));

        // 2. Validação da trilha de auditoria append-only
        Page<PriceHistory> historico = priceHistoryRepository.findByProductIdOrderByCreatedAtDesc(product.getId(), Pageable.unpaged());
        assertThat(historico.getContent()).hasSize(1);

        PriceHistory registro = historico.getContent().get(0);
        assertThat(registro.getOldPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(registro.getNewPrice()).isEqualByComparingTo(precoNovo);
        assertThat(registro.getOrigin()).isEqualTo(PriceOrigin.MANUAL);
        assertThat(registro.getChangedBy().getId()).isEqualTo(adminUser.getId());
        assertThat(registro.getReason()).isEqualTo(request.reason());
    }

    @Test
    @DisplayName("Override abaixo do piso pelo ADMIN deve gerar alerta de MARGIN_BREACH")
    void deveDispararAlertaDeMarginBreachQuandoAdminAbaixoDoPiso() {
        // Piso é 125.00; ADMIN aplica 110.00
        BigDecimal precoAbaixoPiso = new BigDecimal("110.00");
        PriceOverrideRequest request = new PriceOverrideRequest(
                precoAbaixoPiso,
                "Aprovação de queima emergencial de estoque",
                24
        );

        priceOverrideService.overridePrice(product.getId(), request, adminUser);

        Page<Alert> alertas = alertRepository.findAllByOrderByCreatedAtDesc(Pageable.unpaged());
        assertThat(alertRepository.findAll())
                .anyMatch(a -> a.getType() == AlertType.MARGIN_BREACH && a.getProduct().getId().equals(product.getId()));
    }
}