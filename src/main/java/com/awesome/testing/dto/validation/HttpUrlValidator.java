package com.awesome.testing.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        String url = value.toString();
        if (url.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT)) || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    && hasHost(uri);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** A registry-based authority (for example a host with an underscore) has no parsed host but is still a host. */
    private static boolean hasHost(URI uri) {
        String host = uri.getHost() != null ? uri.getHost() : uri.getRawAuthority();
        return host != null && !host.isEmpty() && !host.startsWith(":") && !host.startsWith("@");
    }
}
