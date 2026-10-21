package com.guidaerique.pricing_copilot.catalog.service;

import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.dto.CategoryRequest;
import com.guidaerique.pricing_copilot.catalog.dto.CategoryResponse;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.shared.exception.BusinessRuleException;
import com.guidaerique.pricing_copilot.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        validateCategoryConstraints(request);

        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessRuleException("Já existe uma categoria cadastrada com o nome: " + request.name());
        }

        Category category = new Category(
                request.name(),
                request.vertical(),
                request.minMargin(),
                request.maxMargin(),
                request.maxVariation()
        );

        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        validateCategoryConstraints(request);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com o ID: " + id));

        categoryRepository.findByName(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("O nome informado já pertence a outra categoria: " + request.name());
                });

        category.setName(request.name());
        category.setVertical(request.vertical());
        category.setMinMargin(request.minMargin());
        category.setMaxMargin(request.maxMargin());
        category.setMaxVariation(request.maxVariation());

        return CategoryResponse.fromEntity(category);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoria não encontrada com o ID: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private void validateCategoryConstraints(CategoryRequest request) {
        if (request.minMargin().compareTo(request.maxMargin()) >= 0) {
            throw new BusinessRuleException("A margem mínima (" + request.minMargin()
                    + ") deve ser estritamente menor que a margem máxima (" + request.maxMargin() + ").");
        }
        if (request.maxVariation().compareTo(new BigDecimal("0.5000")) > 0) {
            throw new BusinessRuleException("A variação máxima não pode exceder 50% (0.5000).");
        }
    }
}