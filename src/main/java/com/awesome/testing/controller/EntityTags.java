package com.awesome.testing.controller;

import com.awesome.testing.controller.exception.CustomException;
import org.springframework.http.HttpStatus;

/** Parses the version-based entity tags used by conditional product requests. */
final class EntityTags {

    private EntityTags() {
    }

    /**
     * Returns the version in an If-Match header, or null when the header is
     * absent or {@code *}. Weak tags are accepted. Anything else is a 400.
     */
    static Long parseVersion(String header) {
        if (header == null || header.isBlank() || header.trim().equals("*")) {
            return null;
        }
        Long version = versionOf(header.trim());
        if (version == null) {
            throw new CustomException("If-Match must be a quoted product version such as \"3\"", HttpStatus.BAD_REQUEST);
        }
        return version;
    }

    /** True when an If-None-Match header lists the current version or {@code *}. */
    static boolean matches(String header, Long version) {
        if (header == null || header.isBlank()) {
            return false;
        }
        for (String candidate : header.split(",")) {
            String tag = candidate.trim();
            if (tag.equals("*") || version.equals(versionOf(tag))) {
                return true;
            }
        }
        return false;
    }

    private static Long versionOf(String tag) {
        String value = tag.startsWith("W/") ? tag.substring(2) : tag;
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return null;
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
