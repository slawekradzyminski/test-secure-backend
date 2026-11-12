package com.awesome.testing.traffic;

import com.awesome.testing.entity.TrafficMaskingStateEntity;
import com.awesome.testing.repository.TrafficMaskingStateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-off data migration (catalog X-03): masks the traffic history stored before the current masking rules
 * with those rules, so passwords, tokens and codes captured in clear by older releases are not kept until
 * the retention job deletes them. Runs on start-up while {@code traffic_masking_state} records an older
 * rules version, in batches by id, then records {@link TrafficDataSanitizer#RULES_VERSION}. Masking is
 * idempotent, so an interrupted run simply starts again on the next start.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TrafficHistoryMasker implements ApplicationRunner {

    static final int BATCH_SIZE = 500;
    private static final TypeReference<Map<String, List<String>>> HEADERS = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final TrafficDataSanitizer sanitizer;
    private final TrafficMaskingStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        maskStoredHistory();
    }

    /** Returns the number of rows whose stored content changed. */
    public int maskStoredHistory() {
        TrafficMaskingStateEntity state = stateRepository.findById(TrafficMaskingStateEntity.ID)
                .orElseGet(() -> new TrafficMaskingStateEntity(TrafficMaskingStateEntity.ID, 0));
        if (state.getRulesVersion() >= TrafficDataSanitizer.RULES_VERSION) {
            return 0;
        }
        int changed = 0;
        long lastId = 0;
        while (true) {
            List<StoredRow> rows = jdbcTemplate.query("""
                    SELECT id, path, query_string, request_headers, request_body, response_headers, response_body
                    FROM traffic_log WHERE id > ? ORDER BY id LIMIT ?""",
                    (result, index) -> new StoredRow(result.getLong(1), result.getString(2), result.getString(3),
                            result.getString(4), result.getString(5), result.getString(6), result.getString(7)),
                    lastId, BATCH_SIZE);
            if (rows.isEmpty()) {
                break;
            }
            List<Object[]> updates = new ArrayList<>();
            for (StoredRow row : rows) {
                StoredRow masked = mask(row);
                if (!masked.equals(row)) {
                    updates.add(new Object[] {masked.path(), masked.queryString(), masked.requestHeaders(),
                            masked.requestBody(), length(masked.requestBody()), masked.responseHeaders(),
                            masked.responseBody(), length(masked.responseBody()), row.id()});
                }
            }
            if (!updates.isEmpty()) {
                jdbcTemplate.batchUpdate("""
                        UPDATE traffic_log SET path = ?, query_string = ?, request_headers = ?, request_body = ?,
                            request_body_stored_length = ?, response_headers = ?, response_body = ?,
                            response_body_stored_length = ?
                        WHERE id = ?""", updates);
                changed += updates.size();
            }
            lastId = rows.getLast().id();
        }
        state.setRulesVersion(TrafficDataSanitizer.RULES_VERSION);
        stateRepository.save(state);
        log.info("Masked {} stored traffic records with masking rules version {}", changed,
                TrafficDataSanitizer.RULES_VERSION);
        return changed;
    }

    private StoredRow mask(StoredRow row) {
        String path = row.path();
        return new StoredRow(row.id(), sanitizer.sanitizePath(path), sanitizer.sanitizeQuery(path, row.queryString()),
                maskHeaders(row.requestHeaders()), sanitizer.maskStoredBody(path, row.requestBody()),
                maskHeaders(row.responseHeaders()), sanitizer.maskStoredBody(path, row.responseBody()));
    }

    private String maskHeaders(String storedHeaders) {
        if (storedHeaders == null || storedHeaders.isBlank()) {
            return storedHeaders;
        }
        try {
            Map<String, List<String>> headers = objectMapper.readValue(storedHeaders, HEADERS);
            Map<String, List<String>> masked = sanitizer.sanitizeHeaders(headers);
            return masked.equals(headers) ? storedHeaders : objectMapper.writeValueAsString(masked);
        } catch (RuntimeException unreadable) {
            // Headers that are not the stored name-to-values map cannot be masked selectively.
            return "{}";
        }
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private record StoredRow(long id, String path, String queryString, String requestHeaders, String requestBody,
                             String responseHeaders, String responseBody) {
    }
}
