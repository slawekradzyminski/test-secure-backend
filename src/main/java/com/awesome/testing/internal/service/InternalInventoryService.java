package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.internal.dto.InternalProductDto;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.dto.InventoryExportItemDto;
import com.awesome.testing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Product snapshots and the one-time stock ledger export for warehouse-service. */
@Service
@RequiredArgsConstructor
public class InternalInventoryService {

    static final int MAX_EXPORT_LIMIT = 500;

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<InternalProductDto> products(List<Long> ids) {
        List<ProductEntity> products = ids == null || ids.isEmpty()
                ? productRepository.findAll(Sort.by("id"))
                : productRepository.findAllById(ids).stream()
                        .sorted(Comparator.comparing(ProductEntity::getId))
                        .toList();
        return products.stream().map(InternalProductDto::from).toList();
    }

    /**
     * Contract section 6: up to {@code limit} products with an id above {@code afterProductId}, each
     * with its projected quantity. {@code nextAfterProductId} is null once a page is not full.
     */
    @Transactional(readOnly = true)
    public InventoryExportDto export(Long afterProductId, int limit) {
        if (limit < 1 || limit > MAX_EXPORT_LIMIT) {
            throw new CustomException("limit must be between 1 and " + MAX_EXPORT_LIMIT, HttpStatus.BAD_REQUEST);
        }
        if (afterProductId != null && afterProductId < 0) {
            throw new CustomException("afterProductId must not be negative", HttpStatus.BAD_REQUEST);
        }
        List<ProductEntity> products = productRepository.findAfterId(
                afterProductId == null ? 0 : afterProductId, PageRequest.of(0, limit));
        List<InventoryExportItemDto> items = products.stream()
                .map(product -> new InventoryExportItemDto(product.getId(), product.getStockQuantity()))
                .toList();
        Long next = products.size() == limit ? products.getLast().getId() : null;
        return new InventoryExportDto(items, next);
    }
}
