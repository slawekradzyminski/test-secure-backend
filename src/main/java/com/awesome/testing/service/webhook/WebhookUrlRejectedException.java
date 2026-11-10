package com.awesome.testing.service.webhook;

import java.io.Serial;

/** A webhook URL that must not be called; the message is safe to show to its owner. */
public class WebhookUrlRejectedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public WebhookUrlRejectedException(String message) {
        super(message);
    }

    public WebhookUrlRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
