package com.awesome.testing.entity.fulfilment;

import java.util.regex.Pattern;

public enum Carrier {
    INPOST("^\\d{24}$", "24 digits"),
    DPD("^\\d{14}$", "14 digits"),
    DHL("^\\d{10}$", "10 digits"),
    UPS("^1Z[A-Z0-9]{16}$", "1Z followed by 16 upper-case letters or digits");

    private final Pattern trackingPattern;
    private final String formatDescription;

    Carrier(String trackingPattern, String trackingFormat) {
        this.trackingPattern = Pattern.compile(trackingPattern);
        this.formatDescription = trackingFormat;
    }

    public boolean acceptsTrackingNumber(String trackingNumber) {
        return trackingNumber != null && trackingPattern.matcher(trackingNumber).matches();
    }

    public String trackingFormat() {
        return formatDescription;
    }
}
