package com.awesome.testing.traffic;

import com.awesome.testing.entity.TrafficSessionOwnerEntity;
import com.awesome.testing.repository.TrafficSessionOwnerRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Binds a traffic session to the first authenticated user seen on it (catalog X-03). Reads of a session
 * return the caller's own records, plus the session's anonymous records only to the bound user, so knowing
 * another browser's session id no longer reveals its traffic.
 */
@Service
public class TrafficSessionOwners {

    private final TrafficSessionOwnerRepository repository;
    private final Cache<String, String> owners = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public TrafficSessionOwners(TrafficSessionOwnerRepository repository) {
        this.repository = repository;
    }

    /** Binds the session to the user unless it is already bound; returns the bound user. */
    public String bind(String clientSessionId, String username) {
        String cached = owners.getIfPresent(clientSessionId);
        if (cached != null) {
            return cached;
        }
        String bound = repository.findById(clientSessionId)
                .map(TrafficSessionOwnerEntity::getUsername)
                .orElseGet(() -> insert(clientSessionId, username));
        owners.put(clientSessionId, bound);
        return bound;
    }

    public Optional<String> owner(String clientSessionId) {
        String cached = owners.getIfPresent(clientSessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> stored = repository.findById(clientSessionId).map(TrafficSessionOwnerEntity::getUsername);
        stored.ifPresent(username -> owners.put(clientSessionId, username));
        return stored;
    }

    /** Drops cached bindings after the retention job deleted stored ones. */
    public void evictAll() {
        owners.invalidateAll();
    }

    private String insert(String clientSessionId, String username) {
        try {
            repository.saveAndFlush(new TrafficSessionOwnerEntity(clientSessionId, username, Instant.now()));
            return username;
        } catch (DataIntegrityViolationException concurrentBinding) {
            return repository.findById(clientSessionId)
                    .map(TrafficSessionOwnerEntity::getUsername)
                    .orElseThrow(() -> concurrentBinding);
        }
    }
}
