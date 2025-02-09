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

import static com.awesome.testing.utils.EntityUpdater.updateIfNotNull;

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
        updateIfNotNull(productUpdateDto.getName(), ProductEntity::setName, product);
        updateIfNotNull(productUpdateDto.getDescription(), ProductEntity::setDescription, product);
        updateIfNotNull(productUpdateDto.getPrice(), ProductEntity::setPrice, product);
        updateIfNotNull(productUpdateDto.getStockQuantity(), ProductEntity::setStockQuantity, product);
        updateIfNotNull(productUpdateDto.getCategory(), ProductEntity::setCategory, product);
        updateIfNotNull(productUpdateDto.getImageUrl(), ProductEntity::setImageUrl, product);
    }

}