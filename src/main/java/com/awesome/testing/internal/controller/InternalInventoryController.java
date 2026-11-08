package com.awesome.testing.internal.controller;

import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.internal.dto.InternalProductDto;
import com.awesome.testing.internal.dto.InventoryReceiptDto;
import com.awesome.testing.internal.dto.InventoryReceiptResultDto;
import com.awesome.testing.internal.service.InternalInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Product snapshots and stock receipts for warehouse-service; undocumented in OpenAPI on purpose. */
@RestController
@RequiredArgsConstructor
@RequestMapping(InternalApi.BASE_PATH)
public class InternalInventoryController {

    private final InternalInventoryService internalInventoryService;

    @GetMapping("/products")
    public List<InternalProductDto> products(@RequestParam(required = false) List<Long> ids) {
        return internalInventoryService.products(ids);
    }

    @PostMapping("/inventory/receipts")
    public InventoryReceiptResultDto receive(@Valid @RequestBody InventoryReceiptDto receipt) {
        return internalInventoryService.receive(receipt);
    }
}
