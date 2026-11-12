package com.awesome.testing.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.HttpHelper;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.entity.TrafficMaskingStateEntity;
import com.awesome.testing.repository.TrafficLogRepository;
import com.awesome.testing.repository.TrafficMaskingStateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Catalog X-03: traffic stored by older releases kept passwords, reset passwords, tokens and gift card codes
 * in clear. The start-up migration masks that history with the current rules once.
 */
class TrafficHistoryMaskerTest extends HttpHelper {

    @Autowired private TrafficHistoryMasker masker;
    @Autowired private TrafficMaskingStateRepository stateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TrafficLogRepository repository;

    @Test
    void masksHistoryStoredUnderOlderRulesOnceAndRecordsTheRulesVersion() {
        // given: rows written by a release without masking, and a state from before this release
        long reset = insert("/api/v1/users/password/reset", null,
                "{\"Authorization\":[\"Bearer stored-jwt\"],\"Content-Type\":[\"application/json\"]}",
                "{\"token\":\"stored-reset-token\",\"newPassword\":\"StoredNew123!\",\"confirmPassword\":\"StoredNew123!\"}",
                "{\"token\":\"stored-access-token\",\"username\":\"alice\"}");
        long giftCard = insert("/api/v1/gift-cards/GC-STOR-EDCO-DE12", "code=GC-STOR-EDCO-DE12&page=0", "not json",
                "", "{\"code\":\"GC-STOR-EDCO-DE12\",\"balance\":10}");
        long harmless = insert("/api/v1/products", "page=0", "{\"Accept\":[\"*/*\"]}", "", "[{\"name\":\"Phone\"}]");
        stateRepository.save(new TrafficMaskingStateEntity(TrafficMaskingStateEntity.ID, 0));

        // when
        int changed = masker.maskStoredHistory();
        int secondRun = masker.maskStoredHistory();

        // then
        Map<String, Object> resetRow = row(reset);
        assertThat(resetRow.get("request_body").toString())
                .doesNotContain("stored-reset-token", "StoredNew123!")
                .isEqualTo("{\"token\":\"***\",\"newPassword\":\"***\",\"confirmPassword\":\"***\"}");
        assertThat(resetRow.get("request_body_stored_length")).isEqualTo(resetRow.get("request_body").toString().length());
        assertThat(resetRow.get("response_body").toString()).isEqualTo("{\"token\":\"***\",\"username\":\"alice\"}");
        assertThat(resetRow.get("request_headers").toString()).doesNotContain("stored-jwt").contains("application/json");

        Map<String, Object> giftCardRow = row(giftCard);
        assertThat(giftCardRow.get("path")).isEqualTo("/api/v1/gift-cards/***");
        assertThat(giftCardRow.get("query_string")).isEqualTo("code=***&page=0");
        assertThat(giftCardRow.get("request_headers")).isEqualTo("{}");
        assertThat(giftCardRow.get("response_body").toString()).isEqualTo("{\"code\":\"***\",\"balance\":10}");

        Map<String, Object> harmlessRow = row(harmless);
        assertThat(harmlessRow.get("response_body")).isEqualTo("[{\"name\":\"Phone\"}]");
        assertThat(harmlessRow.get("request_headers")).isEqualTo("{\"Accept\":[\"*/*\"]}");

        assertThat(changed).isGreaterThanOrEqualTo(2);
        assertThat(secondRun).isZero();
        assertThat(stateRepository.findById(TrafficMaskingStateEntity.ID).orElseThrow().getRulesVersion())
                .isEqualTo(TrafficDataSanitizer.RULES_VERSION);
    }

    private long insert(String path, String query, String requestHeaders, String requestBody, String responseBody) {
        return repository.saveAndFlush(TrafficLogEntity.builder()
                .correlationId(UUID.randomUUID().toString())
                .clientSessionId("history-session-1234")
                .timestamp(Instant.now())
                .method("POST")
                .path(path)
                .queryString(query)
                .status(200)
                .requestHeaders(requestHeaders)
                .requestBody(requestBody)
                .requestBodyOriginalLength(requestBody.length())
                .requestBodyStoredLength(requestBody.length())
                .responseHeaders("{}")
                .responseBody(responseBody)
                .responseBodyOriginalLength(responseBody.length())
                .responseBodyStoredLength(responseBody.length())
                .build()).getId();
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("""
                SELECT path, query_string, request_headers, request_body, request_body_stored_length, response_body
                FROM traffic_log WHERE id = ?""", id);
    }
}
