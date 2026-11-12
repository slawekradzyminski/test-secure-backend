package com.awesome.testing.service;

/**
 * The text does not fit in the largest QR code (version 40, error correction
 * level L). The limit is in encoded bytes, not characters: about 2953 bytes of
 * ASCII, fewer for UTF-8 text such as Polish letters (2 bytes) or emoji (4 bytes).
 */
public class QrTextTooLongException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Text is too long for a QR code";

    public QrTextTooLongException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
