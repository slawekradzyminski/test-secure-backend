package com.awesome.testing.service;

import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductListDto;
import com.awesome.testing.dto.product.ProductSummaryDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;

import static com.awesome.testing.utils.EntityUpdater.updateIfNotNull;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductListDto listProducts(int offset, int limit, String category, Boolean inStockOnly) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, safeOffset + safeLimit);

        Page<ProductEntity> result = productRepository.findAll(
                (root, query, cb) -> {
                    query.distinct(true);
                    List<Predicate> predicates = new ArrayList<>();
                    if (category != null && !category.isBlank()) {
                        predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase(Locale.ROOT)));
                    }
                    if (Boolean.TRUE.equals(inStockOnly)) {
                        predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
                    }
                    return predicates.isEmpty()
                            ? cb.conjunction()
                            : cb.and(predicates.toArray(new Predicate[0]));
                },
                pageable);

        List<ProductSummaryDto> content = result.getContent().stream()
                .map(ProductSummaryDto::from)
                .toList();
        int from = Math.min(safeOffset, content.size());
        int to = Math.min(safeOffset + safeLimit, content.size());

        return ProductListDto.builder()
                .products(content.subList(from, to))
                .total(result.getTotalElements())
                .page(safeOffset)
                .size(safeLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        return productRepository.findById(id)
                .map(ProductDto::from)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    @Transactional(readOnly = true)
    public ProductDto getProductByName(String name) {
        return productRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .map(ProductDto::from)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    @Transactional
    public ProductDto createProduct(ProductCreateDto productCreateDto) {
        ProductEntity product = ProductEntity.from(productCreateDto);
        productRepository.save(product);
        return ProductDto.from(product);
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductUpdateDto productUpdateDto) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        applyProductUpdates(productUpdateDto, product);
        productRepository.saveAndFlush(product);
        return ProductDto.from(product);
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    productRepository.delete(product);
                    return true;
                })
                .orElse(false);
    }

    private void applyProductUpdates(ProductUpdateDto productUpdateDto, ProductEntity product) {
        updateIfNotNull(productUpdateDto.getName(), ProductEntity::setName, product);
        updateIfNotNull(productUpdateDto.getDescription(), ProductEntity::setDescription, product);
        updateIfNotNull(productUpdateDto.getPrice(), ProductEntity::setPrice, product);
        updateIfNotNull(productUpdateDto.getStockQuantity(), ProductEntity::setStockQuantity, product);
        updateIfNotNull(productUpdateDto.getCategory(), ProductEntity::setCategory, product);
        updateIfNotNull(productUpdateDto.getImageUrl(), ProductEntity::setImageUrl, product);
    }

}
