package com.guidaerique.pricing_copilot.pricing.service;

import com.guidaerique.pricing_copilot.audit.domain.PriceHistory;
import com.guidaerique.pricing_copilot.audit.domain.PriceOrigin;
import com.guidaerique.pricing_copilot.audit.repository.PriceHistoryRepository;
import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.dto.ProductResponse;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBoundsCalculator;
import com.guidaerique.pricing_copilot.pricing.domain.event.PriceChangedEvent;
import com.guidaerique.pricing_copilot.pricing.dto.PriceOverrideRequest;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.security.domain.Role;
import com.guidaerique.pricing_copilot.security.repository.AppUserRepository;
import com.guidaerique.pricing_copilot.shared.exception.BusinessRuleException;
import com.guidaerique.pricing_copilot.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class PriceOverrideService {

    private static final int DIVISION_SCALE = 12;
    private static final int MONEY_SCALE = 2;
    private static final int DEFAULT_LOCK_HOURS = 24;

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PriceBoundsCalculator boundsCalculator = new PriceBoundsCalculator();

    public PriceOverrideService(
            ProductRepository productRepository,
            PriceHistoryRepository priceHistoryRepository,
            AppUserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductResponse overridePrice(Long productId, PriceOverrideRequest request, AppUser currentUser) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com o ID: " + productId));

        Category category = product.getCategory();
        BigDecimal newPrice = request.newPrice();
        BigDecimal oldPrice = product.getCurrentPrice();
        BigDecimal cost = product.getCostPrice();

        // 1. Resolução segura do usuário e perfil (RBAC)
        Role userRole = resolveUserRole(currentUser);
        AppUser author = resolveAuthor(currentUser);

        // 2. Cálculo dos limites de margem nominais estritos
        BigDecimal pisoMargem = cost.divide(BigDecimal.ONE.subtract(category.getMinMargin()), DIVISION_SCALE, RoundingMode.CEILING)
                .setScale(MONEY_SCALE, RoundingMode.CEILING);
        BigDecimal tetoMargem = cost.divide(BigDecimal.ONE.subtract(category.getMaxMargin()), DIVISION_SCALE, RoundingMode.FLOOR)
                .setScale(MONEY_SCALE, RoundingMode.FLOOR);

        // 3. Validação de Governança e RBAC
        if (userRole == Role.OPERATOR) {
            if (newPrice.compareTo(pisoMargem) < 0 || newPrice.compareTo(tetoMargem) > 0) {
                throw new AccessDeniedException(String.format(
                        "Operador não possui autorização para aplicar preço fora do intervalo de margem da categoria [%s, %s]",
                        pisoMargem, tetoMargem
                ));
            }
        } else if (userRole == Role.ADMIN) {
            if (request.reason() == null || request.reason().trim().length() < 10) {
                throw new BusinessRuleException("A sobrescrita por ADMIN exige justificativa formal com no mínimo 10 caracteres.");
            }
        } else {
            throw new AccessDeniedException("O perfil informado não possui permissão para sobrescrever preços.");
        }

        // 4. Aplicação do lock temporário contra a IA
        int lockDuration = (request.lockHours() != null) ? request.lockHours() : DEFAULT_LOCK_HOURS;
        product.setManualLockUntil(LocalDateTime.now().plusHours(lockDuration));

        // 5. Atualização de preço com controle de versão otimista (@Version)
        product.setCurrentPrice(newPrice);
        Product savedProduct = productRepository.saveAndFlush(product);

        // 6. Persistência atômica do histórico imutável
        PriceHistory history = new PriceHistory(
                savedProduct,
                oldPrice,
                newPrice,
                PriceOrigin.MANUAL,
                author,
                request.reason()
        );
        priceHistoryRepository.save(history);

        // 7. Cálculo dos limites de variação e disparo de evento
        PriceBounds bounds = boundsCalculator.calculate(
                cost,
                oldPrice,
                category.getMinMargin(),
                category.getMaxMargin(),
                category.getMaxVariation()
        );

        eventPublisher.publishEvent(new PriceChangedEvent(
                savedProduct,
                oldPrice,
                newPrice,
                cost,
                category.getMinMargin(),
                category.getMaxMargin(),
                pisoMargem,
                tetoMargem,
                bounds,
                true
        ));

        return ProductResponse.fromEntity(savedProduct);
    }

    @Transactional
    public void unlockProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com o ID: " + productId));
        product.setManualLockUntil(null);
        productRepository.save(product);
    }

    private Role resolveUserRole(AppUser currentUser) {
        if (currentUser != null && currentUser.getRole() != null) {
            return currentUser.getRole();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return Role.ADMIN;
            }
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"))) {
                return Role.OPERATOR;
            }
        }
        return Role.OPERATOR;
    }

    private AppUser resolveAuthor(AppUser currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }
}