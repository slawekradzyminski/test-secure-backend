package com.awesome.testing.service.password;

import com.awesome.testing.dto.email.EmailDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalEmailOutbox {

    private final Queue<StoredEmail> outbox = new ConcurrentLinkedQueue<>();

    public void store(String destination, EmailDto payload) {
        outbox.add(new StoredEmail(Instant.now(), destination, payload));
    }

    public List<StoredEmail> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(outbox));
    }

    public void clear() {
        outbox.clear();
    }

    @Value
    public static class StoredEmail {
        Instant timestamp;
        String destination;
        EmailDto payload;
    }
}
