package com.awesome.testing.internal.controller;

import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.internal.dto.FulfilmentUpdateDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.service.InternalOrderService;
import com.awesome.testing.internal.service.InternalRefundService;
import com.awesome.testing.internal.dto.OrderRefundDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orders as seen by warehouse-service. Deliberately undocumented in OpenAPI:
 * the package is excluded from springdoc and from the endpoint documentation
 * coverage test.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(InternalApi.BASE_PATH + "/orders")
public class InternalOrderController {

    private final InternalOrderService internalOrderService;
    private final InternalRefundService internalRefundService;

    @GetMapping("/{id}")
    public InternalOrderDto get(@PathVariable Long id) {
        return internalOrderService.get(id);
    }

    /** Snapshots of the given orders (any status); unknown ids are skipped. At most 100 ids. */
    @GetMapping(params = "ids")
    public List<InternalOrderDto> findByIds(@RequestParam List<Long> ids) {
        return internalOrderService.findByIds(ids);
    }

    @GetMapping
    public PageDto<InternalOrderDto> find(
            @RequestParam(defaultValue = "PAID") OrderStatus status,
            @RequestParam(defaultValue = "NONE") FulfilmentState fulfilment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return internalOrderService.find(status, fulfilment, page, size);
    }

    @PutMapping("/{id}/fulfilment")
    public InternalOrderDto updateFulfilment(@PathVariable Long id, @Valid @RequestBody FulfilmentUpdateDto request) {
        return internalOrderService.applyFulfilment(id, request.state());
    }

    @PostMapping("/{id}/refunds")
    public InternalOrderDto refund(@PathVariable Long id, @Valid @RequestBody OrderRefundDto request) {
        return internalRefundService.refund(id, request);
    }

    @PostMapping("/{id}/warehouse-cancel")
    public InternalOrderDto warehouseCancel(@PathVariable Long id) {
        return internalOrderService.warehouseCancel(id);
    }
}
