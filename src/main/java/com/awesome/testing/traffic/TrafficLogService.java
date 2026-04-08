package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class TrafficLogService {

    private final TrafficLogRepository trafficLogRepository;

    public TrafficLogService(TrafficLogRepository trafficLogRepository) {
        this.trafficLogRepository = trafficLogRepository;
    }

    public TrafficLogEntity save(TrafficLogEntity entity) {
        return trafficLogRepository.save(entity);
    }

    public Page<TrafficLogEntryDto> findLogs(String method,
                                             Integer status,
                                             String pathContains,
                                             String text,
                                             Instant from,
                                             Instant to,
                                             Pageable pageable) {
        return trafficLogRepository.findAll(buildSpecification(method, status, pathContains, text, from, to), pageable)
                .map(this::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationId(String correlationId) {
        return trafficLogRepository.findByCorrelationId(correlationId).map(this::toDto);
    }

    private TrafficLogEntryDto toDto(TrafficLogEntity entity) {
        return TrafficLogEntryDto.builder()
                .correlationId(entity.getCorrelationId())
                .timestamp(entity.getTimestamp())
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

    private Specification<TrafficLogEntity> buildSpecification(String method,
                                                               Integer status,
                                                               String pathContains,
                                                               String text,
                                                               Instant from,
                                                               Instant to) {
        List<Specification<TrafficLogEntity>> specifications = new ArrayList<>();
        addIfPresent(specifications, equalsIgnoreCase("method", method));
        addIfPresent(specifications, equalsValue("status", status));
        addIfPresent(specifications, likeIgnoreCase("path", pathContains));
        addIfPresent(specifications, instantGreaterThanOrEqualTo("timestamp", from));
        addIfPresent(specifications, instantLessThanOrEqualTo("timestamp", to));
        addIfPresent(specifications, fullTextLike(text));

        Specification<TrafficLogEntity> specification = null;
        for (Specification<TrafficLogEntity> candidate : specifications) {
            specification = specification == null ? candidate : specification.and(candidate);
        }
        return specification;
    }

    private void addIfPresent(List<Specification<TrafficLogEntity>> specifications,
                              Specification<TrafficLogEntity> specification) {
        if (specification != null) {
            specifications.add(specification);
        }
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
                cb.like(cb.lower(root.get("path")), normalized),
                cb.like(cb.lower(cb.coalesce(root.get("queryString"), "")), normalized),
                cb.like(cb.lower(cb.coalesce(root.get("requestBody"), "")), normalized),
                cb.like(cb.lower(cb.coalesce(root.get("responseBody"), "")), normalized)
        );
    }
}
