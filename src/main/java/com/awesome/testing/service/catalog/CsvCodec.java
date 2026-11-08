package com.awesome.testing.service.catalog;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 reader and writer used by the product import and export. */
public final class CsvCodec {

    private static final String FORMULA_PREFIXES = "=+-@\t\r";
    private static final char QUOTE = '"';
    private static final char SEPARATOR = ',';
    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    private CsvCodec() {
    }

    /** Escapes a numeric or identifier cell. */
    public static String cell(Object value) {
        return quoteIfNeeded(value == null ? "" : value.toString());
    }

    /**
     * Escapes a free-text cell and neutralises spreadsheet formulas by
     * prefixing a single quote, as recommended by OWASP for CSV injection.
     */
    public static String textCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return quoteIfNeeded(safe);
    }

    private static String quoteIfNeeded(String value) {
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    /**
     * Parses CSV content into records. Quoted fields may contain commas,
     * doubled quotes, and line breaks. A leading UTF-8 byte order mark is ignored.
     *
     * @throws IllegalArgumentException when a quoted field is not terminated or
     *                                  text follows a closing quote
     */
    public static List<List<String>> parse(String content) {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;
        int line = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    if (c == LINE_FEED) {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == QUOTE) {
                if (field.length() > 0 || afterQuote) {
                    throw new IllegalArgumentException("Unexpected quote on line " + line);
                }
                inQuotes = true;
            } else if (c == SEPARATOR) {
                record.add(field.toString());
                field.setLength(0);
                afterQuote = false;
            } else if (c == CARRIAGE_RETURN || c == LINE_FEED) {
                record.add(field.toString());
                field.setLength(0);
                afterQuote = false;
                records.add(record);
                record = new ArrayList<>();
                if (c == CARRIAGE_RETURN && i + 1 < text.length() && text.charAt(i + 1) == LINE_FEED) {
                    i++;
                }
                line++;
            } else {
                if (afterQuote) {
                    throw new IllegalArgumentException("Unexpected character after closing quote on line " + line);
                }
                field.append(c);
            }
            i++;
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted field starting before line " + line);
        }
        if (field.length() > 0 || !record.isEmpty() || afterQuote) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
