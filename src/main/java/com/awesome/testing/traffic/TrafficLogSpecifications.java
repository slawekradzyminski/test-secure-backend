package com.awesome.testing.traffic;

import com.awesome.testing.entity.TrafficLogEntity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class TrafficLogSpecifications {
    private TrafficLogSpecifications() {}

    public static Specification<TrafficLogEntity> matching(
            String clientSessionId,
            String method,
            Integer status,
            String pathContains,
            String text,
            Instant from,
            Instant to) {
        return Specification.allOf(
                Arrays.asList(
                                equalsSession(clientSessionId),
                                equalsIgnoreCase("method", method),
                                equalsValue("status", status),
                                likeIgnoreCase("path", pathContains),
                                instantGreaterThanOrEqualTo("timestamp", from),
                                instantLessThanOrEqualTo("timestamp", to),
                                fullTextLike(text))
                        .stream()
                        .filter(Objects::nonNull)
                        .toList());
    }

    private static Specification<TrafficLogEntity> equalsSession(String value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get("clientSessionId"), value);
    }

    private static Specification<TrafficLogEntity> equalsIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get(field)), value.toLowerCase(Locale.ROOT));
    }

    private static Specification<TrafficLogEntity> equalsValue(String field, Integer value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<TrafficLogEntity> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = "%" + value.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), normalized);
    }

    private static Specification<TrafficLogEntity> instantGreaterThanOrEqualTo(String field, Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value);
    }

    private static Specification<TrafficLogEntity> instantLessThanOrEqualTo(String field, Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value);
    }

    private static Specification<TrafficLogEntity> fullTextLike(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = "%" + value.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) ->
                cb.or(
                        likeLower(cb, root.get("path"), normalized),
                        likeLower(cb, root.get("clientSessionId"), normalized),
                        likeLower(cb, root.get("queryString"), normalized),
                        likeLower(cb, root.get("requestBody").as(String.class), normalized),
                        likeLower(cb, root.get("responseBody").as(String.class), normalized));
    }

    private static Predicate likeLower(CriteriaBuilder cb, Expression<String> expression, String normalized) {
        return cb.like(cb.lower(cb.coalesce(expression, "")), normalized);
    }
}
