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
 * header the read is limited to that session. Anonymous callers, possible only with legacy public access,
 * see nothing.
 */
@Service
@RequiredArgsConstructor
public class TrafficLogService {
    private final TrafficLogRepository trafficLogRepository;
    private final TrafficLogMapper mapper;
    private final TrafficLogBuffer buffer;
    private final TrafficSessionOwners sessionOwners;

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
        if (reader == null) {
            return Page.empty(pageable);
        }
        buffer.flushForRead();
        Specification<TrafficLogEntity> filters = TrafficLogSpecifications.matching(
                clientSessionId, method, status, pathContains, text, from, to);
        return trafficLogRepository
                .findAll(filters.and(TrafficLogSpecifications.visibleTo(
                        reader, readsAnonymousRecords(reader, clientSessionId))), pageable)
                .map(mapper::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationId(String reader, String correlationId, String clientSessionId) {
        if (reader == null) {
            return Optional.empty();
        }
        buffer.flushForRead();
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
