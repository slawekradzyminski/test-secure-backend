package com.awesome.testing.service;

import com.awesome.testing.dto.ProductCreateDto;
import com.awesome.testing.dto.ProductUpdateDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductEntity> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ProductEntity> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public ProductEntity createProduct(ProductCreateDto productCreateDto) {
        ProductEntity product = ProductEntity.from(productCreateDto);
        return productRepository.save(product);
    }

    @Transactional
    public Optional<ProductEntity> updateProduct(Long id, ProductUpdateDto productUpdateDto) {
        return productRepository.findById(id)
                .map(product -> {
                    toUpdatedProduct(productUpdateDto, product);
                    return productRepository.save(product);
                });
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

    private void toUpdatedProduct(ProductUpdateDto productUpdateDto, ProductEntity product) {
        if (productUpdateDto.getName() != null) {
            product.setName(productUpdateDto.getName());
        }
        if (productUpdateDto.getDescription() != null) {
            product.setDescription(productUpdateDto.getDescription());
        }
        if (productUpdateDto.getPrice() != null) {
            product.setPrice(productUpdateDto.getPrice());
        }
        if (productUpdateDto.getStockQuantity() != null) {
            product.setStockQuantity(productUpdateDto.getStockQuantity());
        }
        if (productUpdateDto.getCategory() != null) {
            product.setCategory(productUpdateDto.getCategory());
        }
        if (productUpdateDto.getImageUrl() != null) {
            product.setImageUrl(productUpdateDto.getImageUrl());
        }
    }

}