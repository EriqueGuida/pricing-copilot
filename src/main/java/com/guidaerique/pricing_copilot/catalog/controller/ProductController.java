package com.guidaerique.pricing_copilot.catalog.controller;

import com.guidaerique.pricing_copilot.catalog.dto.ProductCreateRequest;
import com.guidaerique.pricing_copilot.catalog.dto.ProductResponse;
import com.guidaerique.pricing_copilot.catalog.dto.ProductUpdateRequest;
import com.guidaerique.pricing_copilot.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Produtos", description = "Manutenção cadastral de produtos de catálogo")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    @Operation(summary = "Consulta paginada do catálogo de produtos")
    public ResponseEntity<Page<ProductResponse>> listAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'EXECUTIVE')")
    @Operation(summary = "Recupera dados do produto por ID")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Cadastra um novo produto com validação de margem inicial")
    @ApiResponse(responseCode = "201", description = "Produto cadastrado com sucesso")
    public ResponseEntity<ProductResponse> create(@RequestBody @Valid ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Atualiza dados cadastrais e custo do produto")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id, @RequestBody @Valid ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }
}