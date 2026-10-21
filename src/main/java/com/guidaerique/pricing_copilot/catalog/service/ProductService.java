package com.guidaerique.pricing_copilot.catalog.service;

import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Product;
import com.guidaerique.pricing_copilot.catalog.dto.ProductCreateRequest;
import com.guidaerique.pricing_copilot.catalog.dto.ProductResponse;
import com.guidaerique.pricing_copilot.catalog.dto.ProductUpdateRequest;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.catalog.repository.ProductRepository;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBounds;
import com.guidaerique.pricing_copilot.pricing.domain.PriceBoundsCalculator;
import com.guidaerique.pricing_copilot.shared.exception.BusinessRuleException;
import com.guidaerique.pricing_copilot.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PriceBoundsCalculator boundsCalculator = new PriceBoundsCalculator();

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com o ID: " + id));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleException("Já existe um produto com o SKU: " + request.sku());
        }
        if (request.ean() != null && productRepository.existsByEan(request.ean())) {
            throw new BusinessRuleException("Já existe um produto com o EAN: " + request.ean());
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + request.categoryId()));

        validateInitialPriceWithinMargins(request.costPrice(), request.currentPrice(), category);

        Product product = new Product(
                request.sku(),
                request.ean(),
                request.name(),
                category,
                request.costPrice(),
                request.currentPrice()
        );

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com o ID: " + id));

        if (request.ean() != null) {
            productRepository.findByEan(request.ean())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new BusinessRuleException("O EAN informado já pertence a outro produto: " + request.ean());
                    });
            product.setEan(request.ean());
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + request.categoryId()));

        product.setName(request.name());
        product.setCategory(category);
        product.setCostPrice(request.costPrice());

        return ProductResponse.fromEntity(product);
    }

    private void validateInitialPriceWithinMargins(BigDecimal cost, BigDecimal initialPrice, Category category) {
        // Valida se o preço de cadastro respeita a faixa de margem da categoria
        PriceBounds bounds = boundsCalculator.calculate(
                cost,
                initialPrice,
                category.getMinMargin(),
                category.getMaxMargin(),
                category.getMaxVariation()
        );

        if (initialPrice.compareTo(bounds.minPrice()) < 0 || initialPrice.compareTo(bounds.maxPrice()) > 0) {
            throw new BusinessRuleException(String.format(
                    "O preço inicial informado (%s) viola os limites operacionais permitidos pela categoria [%s, %s]",
                    initialPrice, bounds.minPrice(), bounds.maxPrice()
            ));
        }
    }
}