package com.guidaerique.pricing_copilot.pricing;

import com.guidaerique.pricing_copilot.IntegrationTest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OptimisticLockingIT {

    @Autowired
    private PriceOverrideService priceOverrideService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    @DisplayName("Dois updates simultâneos no mesmo produto devem acionar OptimisticLockingFailureException em uma das threads")
    void deveDetectarConcorrenciaOtimistaComSucesso() throws InterruptedException {
        Category category = categoryRepository.save(new Category(
                "Cat-Lock-" + UUID.randomUUID(),
                Vertical.COMPUTER_GAMING,
                new BigDecimal("0.1000"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.2000")
        ));

        Product product = productRepository.save(new Product(
                "SKU-LCK-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Placa de Vídeo RTX 4070",
                category,
                new BigDecimal("1000.00"),
                new BigDecimal("1300.00")
        ));

        AppUser user = userRepository.save(new AppUser(
                "lock-user-" + UUID.randomUUID() + "@lock.com",
                "secret",
                Role.ADMIN
        ));

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latchReady = new CountDownLatch(numberOfThreads);
        CountDownLatch latchStart = new CountDownLatch(1);

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger falhasOtimistas = new AtomicInteger();

        for (int i = 0; i < numberOfThreads; i++) {
            final BigDecimal novoPreco = new BigDecimal("1400.00").add(new BigDecimal(i * 10));
            executor.submit(() -> {
                latchReady.countDown();
                try {
                    latchStart.await(); // Aguarda todas as threads estarem prontas para disparo simultâneo
                    priceOverrideService.overridePrice(
                            product.getId(),
                            new PriceOverrideRequest(novoPreco, "Atualização simultânea concorrente", 24),
                            user
                    );
                    sucessos.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    falhasOtimistas.incrementAndGet();
                } catch (Exception e) {
                    // Erros empacotados pelo container transacional do Spring
                    if (e.getCause() instanceof org.hibernate.StaleObjectStateException
                            || e.getMessage().contains("Row was updated or deleted by another transaction")) {
                        falhasOtimistas.incrementAndGet();
                    }
                }
            });
        }

        latchReady.await();
        latchStart.countDown(); // Libera as threads simultaneamente
        executor.shutdown();

        // Aguarda a finalização
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        // Exatamente 1 das threads deve comitar a transação e a outra deve falhar pelo @Version
        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(falhasOtimistas.get()).isEqualTo(1);
    }
}