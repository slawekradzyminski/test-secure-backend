package com.awesome.testing.internal.controller;

import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.event.EventFeedService;
import com.awesome.testing.internal.InternalApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The durable commerce event feed polled by warehouse-service and payment-service (both
 * callers, see {@link com.awesome.testing.internal.InternalApiConfig}). Parameters are read as
 * strings so every malformed value gets the same 400 body. Undocumented in OpenAPI like the
 * rest of the internal API.
 */
@RestController
@RequiredArgsConstructor
public class InternalEventController {

    private final EventFeedService eventFeedService;

    @GetMapping(path = InternalApi.BASE_PATH + "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public EventFeedDto events(@RequestParam(required = false) String after,
                               @RequestParam(required = false) String limit,
                               @RequestParam(required = false) String types) {
        return eventFeedService.read(after, limit, types);
    }
}
