package com.awesome.testing.internal.controller;

import com.awesome.testing.dto.stock.StockLevelsDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.internal.dto.InternalProductDto;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.service.InternalInventoryService;
import com.awesome.testing.service.stock.StockProjection;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Product snapshots, stock level pushes and the stock export for warehouse-service;
 * undocumented in OpenAPI on purpose.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(InternalApi.BASE_PATH)
public class InternalInventoryController {

    private final InternalInventoryService internalInventoryService;
    private final StockProjection stockProjection;

    @GetMapping("/products")
    public List<InternalProductDto> products(@RequestParam(required = false) List<Long> ids) {
        return internalInventoryService.products(ids);
    }

    /** Contract section 3.3: warehouse-service pushes ledger levels into the stock projection. */
    @PostMapping("/inventory/levels")
    public StockLevelsResultDto levels(@Valid @RequestBody StockLevelsDto request) {
        return stockProjection.applyLevels(request.levels());
    }

    /** Contract section 6: the one-time import of the backend's stock ledger into warehouse-service. */
    @GetMapping("/inventory/export")
    public InventoryExportDto export(
            @RequestParam(required = false) Long afterProductId,
            @RequestParam(defaultValue = "500") int limit) {
        return internalInventoryService.export(afterProductId, limit);
    }
}
