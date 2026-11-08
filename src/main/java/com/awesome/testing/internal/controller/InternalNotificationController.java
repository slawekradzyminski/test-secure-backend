package com.awesome.testing.internal.controller;

import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.internal.service.InternalNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer emails requested by warehouse-service; undocumented in OpenAPI on purpose. */
@RestController
@RequiredArgsConstructor
@RequestMapping(InternalApi.BASE_PATH + "/notifications")
public class InternalNotificationController {

    private final InternalNotificationService internalNotificationService;

    @PostMapping
    public ResponseEntity<Void> notify(@Valid @RequestBody InternalNotificationDto notification) {
        internalNotificationService.notify(notification);
        return ResponseEntity.accepted().build();
    }
}
