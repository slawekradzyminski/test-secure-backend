package com.awesome.testing.service;

import com.awesome.testing.dto.ProductCreateDto;
import com.awesome.testing.dto.ProductDto;
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
    public Optional<ProductEntity> updateProduct(Long id, ProductDto productDTO) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setName(productDTO.getName());
                    product.setDescription(productDTO.getDescription());
                    product.setPrice(productDTO.getPrice());
                    product.setStockQuantity(productDTO.getStockQuantity());
                    product.setCategory(productDTO.getCategory());
                    product.setImageUrl(productDTO.getImageUrl());
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
} 