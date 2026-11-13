package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads traffic for one caller (catalog X-03). A caller sees only records they made while authenticated,
 * plus the anonymous records (for example a failed sign-in) of a session bound to them; with a session
 * header the read is limited to that session. Anonymous callers are possible only with legacy public access
 * (owner decision 2026-11-13, the course API relies on it): they read the session given in the header, or every
 * record without one, as before 3.19.0.
 */
@Service
@RequiredArgsConstructor
public class TrafficLogService {
    private final TrafficLogRepository trafficLogRepository;
    private final TrafficLogMapper mapper;
    private final TrafficLogBuffer buffer;
    private final TrafficSessionOwners sessionOwners;
    private final TrafficProperties properties;

    public void save(TrafficLogEntity entity) {
        buffer.enqueue(entity);
    }

    public Page<TrafficLogEntryDto> findLogs(
            String reader,
            String clientSessionId,
            String method,
            Integer status,
            String pathContains,
            String text,
            Instant from,
            Instant to,
            Pageable pageable) {
        if (reader == null && !properties.isLegacyPublicAccess()) {
            return Page.empty(pageable);
        }
        buffer.flushForRead();
        Specification<TrafficLogEntity> filters = TrafficLogSpecifications.matching(
                clientSessionId, method, status, pathContains, text, from, to);
        if (reader == null) {
            // Legacy public access (the course API on awesome.byst.re): anonymous callers read the given
            // session's records, or every record without a session header, as before 3.19.0.
            return trafficLogRepository.findAll(filters, pageable).map(mapper::toDto);
        }
        return trafficLogRepository
                .findAll(filters.and(TrafficLogSpecifications.visibleTo(
                        reader, readsAnonymousRecords(reader, clientSessionId))), pageable)
                .map(mapper::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationId(String reader, String correlationId, String clientSessionId) {
        if (reader == null && !properties.isLegacyPublicAccess()) {
            return Optional.empty();
        }
        buffer.flushForRead();
        if (reader == null) {
            // Legacy public access: the pre-3.19.0 lookups, scoped to the session header when one is sent.
            return (clientSessionId == null
                            ? trafficLogRepository.findByCorrelationId(correlationId)
                            : trafficLogRepository.findByCorrelationIdAndClientSessionId(correlationId, clientSessionId))
                    .map(mapper::toDto);
        }
        boolean anonymousRecords = readsAnonymousRecords(reader, clientSessionId);
        return trafficLogRepository.findByCorrelationId(correlationId)
                .filter(entity -> clientSessionId == null || clientSessionId.equals(entity.getClientSessionId()))
                .filter(entity -> reader.equals(entity.getOwnerUsername())
                        || (anonymousRecords && entity.getOwnerUsername() == null))
                .map(mapper::toDto);
    }

    private boolean readsAnonymousRecords(String reader, String clientSessionId) {
        return clientSessionId != null
                && sessionOwners.owner(clientSessionId).filter(owner -> Objects.equals(owner, reader)).isPresent();
    }
}
