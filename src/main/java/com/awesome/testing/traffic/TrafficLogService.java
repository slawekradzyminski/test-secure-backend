package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrafficLogService {

    private final TrafficLogRepository trafficLogRepository;

    public TrafficLogEntity save(TrafficLogEntity entity) {
        return trafficLogRepository.save(entity);
    }

    public Page<TrafficLogEntryDto> findLogs(String clientSessionId,
                                             String method,
                                             Integer status,
                                             String pathContains,
                                             String text,
                                             Instant from,
                                             Instant to,
                                             Pageable pageable) {
        return trafficLogRepository.findAll(
                        buildSpecification(clientSessionId, method, status, pathContains, text, from, to),
                        pageable
                )
                .map(this::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationId(String correlationId) {
        return trafficLogRepository.findByCorrelationId(correlationId).map(this::toDto);
    }

    private TrafficLogEntryDto toDto(TrafficLogEntity entity) {
        return TrafficLogEntryDto.builder()
                .correlationId(entity.getCorrelationId())
                .timestamp(entity.getTimestamp())
                .clientSessionId(entity.getClientSessionId())
                .method(entity.getMethod())
                .path(entity.getPath())
                .queryString(entity.getQueryString())
                .status(entity.getStatus())
                .durationMs(entity.getDurationMs())
                .requestHeaders(entity.getRequestHeaders())
                .requestBody(entity.getRequestBody())
                .responseHeaders(entity.getResponseHeaders())
                .responseBody(entity.getResponseBody())
                .build();
    }

    private Specification<TrafficLogEntity> buildSpecification(String clientSessionId,
                                                               String method,
                                                               Integer status,
                                                               String pathContains,
                                                               String text,
                                                               Instant from,
                                                               Instant to) {
        Specification<TrafficLogEntity> specification = null;
        for (Specification<TrafficLogEntity> candidate : Arrays.asList(
                equalsIgnoreCase("clientSessionId", clientSessionId),
                equalsIgnoreCase("method", method),
                equalsValue("status", status),
                likeIgnoreCase("path", pathContains),
                instantGreaterThanOrEqualTo("timestamp", from),
                instantLessThanOrEqualTo("timestamp", to),
                fullTextLike(text)
        )) {
            if (candidate != null) {
                specification = specification == null ? candidate : specification.and(candidate);
            }
        }
        return specification;
    }

    private Specification<TrafficLogEntity> equalsIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get(field)), value.toLowerCase());
    }

    private Specification<TrafficLogEntity> equalsValue(String field, Integer value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private Specification<TrafficLogEntity> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), normalized);
    }

    private Specification<TrafficLogEntity> instantGreaterThanOrEqualTo(String field, Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value);
    }

    private Specification<TrafficLogEntity> instantLessThanOrEqualTo(String field, Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value);
    }

    private Specification<TrafficLogEntity> fullTextLike(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = "%" + value.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                likeLower(cb, root.get("path"), normalized),
                likeLower(cb, root.get("clientSessionId"), normalized),
                likeLower(cb, root.get("queryString"), normalized),
                likeLower(cb, root.get("requestBody").as(String.class), normalized),
                likeLower(cb, root.get("responseBody").as(String.class), normalized)
        );
    }

    private Predicate likeLower(jakarta.persistence.criteria.CriteriaBuilder cb,
                                                             Expression<String> expression,
                                                             String normalized) {
        return cb.like(cb.lower(cb.coalesce(expression, "")), normalized);
    }
}
