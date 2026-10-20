package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.entity.TrafficLogEntity;
import com.awesome.testing.repository.TrafficLogRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrafficLogService {
    private final TrafficLogRepository trafficLogRepository;
    private final TrafficLogMapper mapper;
    private final TrafficLogBuffer buffer;

    public void save(TrafficLogEntity entity) {
        buffer.enqueue(entity);
    }

    public Page<TrafficLogEntryDto> findLogs(
            String clientSessionId,
            String method,
            Integer status,
            String pathContains,
            String text,
            Instant from,
            Instant to,
            Pageable pageable) {
        buffer.flushForRead();
        return trafficLogRepository
                .findAll(
                        TrafficLogSpecifications.matching(
                                clientSessionId, method, status, pathContains, text, from, to),
                        pageable)
                .map(mapper::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationIdAndClientSessionId(
            String correlationId, String clientSessionId) {
        buffer.flushForRead();
        return trafficLogRepository
                .findByCorrelationIdAndClientSessionId(correlationId, clientSessionId)
                .map(mapper::toDto);
    }

    public Optional<TrafficLogEntryDto> findByCorrelationId(String correlationId) {
        buffer.flushForRead();
        return trafficLogRepository.findByCorrelationId(correlationId).map(mapper::toDto);
    }
}
