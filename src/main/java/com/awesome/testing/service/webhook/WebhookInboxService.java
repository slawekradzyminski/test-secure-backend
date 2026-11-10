package com.awesome.testing.service.webhook;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookInboxRequestDto;
import com.awesome.testing.entity.webhook.WebhookInboxEntity;
import com.awesome.testing.entity.webhook.WebhookInboxRequestEntity;
import com.awesome.testing.repository.webhook.WebhookInboxRepository;
import com.awesome.testing.repository.webhook.WebhookInboxRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in webhook receivers. A subscription whose URL is {@code inbox://<token>} is
 * delivered in-process: the signed request is stored here instead of being sent over
 * HTTP, and its owner reads it back through the API.
 */
@Service
public class WebhookInboxService {

    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int TOKEN_BYTES = 24;
    private static final String NOT_FOUND = "Webhook inbox not found";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final WebhookInboxRepository inboxes;
    private final WebhookInboxRequestRepository inboxRequests;
    private final WebhookSecrets secrets;
    private final WebhookProperties properties;
    private final Clock clock;

    public WebhookInboxService(WebhookInboxRepository inboxes, WebhookInboxRequestRepository requests,
                               WebhookSecrets secrets, WebhookProperties properties, Clock clock) {
        this.inboxes = inboxes;
        this.inboxRequests = requests;
        this.secrets = secrets;
        this.properties = properties;
        this.clock = clock;
    }

    /** The token of an {@code inbox://} URL, or empty for any other URL. */
    public static Optional<String> token(String url) {
        if (url == null || !url.regionMatches(true, 0, WebhookInboxDto.SCHEME_PREFIX, 0,
                WebhookInboxDto.SCHEME_PREFIX.length())) {
            return Optional.empty();
        }
        return Optional.of(url.substring(WebhookInboxDto.SCHEME_PREFIX.length()));
    }

    @Transactional
    public WebhookInboxDto create(String username) {
        if (inboxes.countByUsername(username) >= properties.getMaxInboxesPerUser()) {
            throw new CustomException("You can have at most " + properties.getMaxInboxesPerUser() + " webhook inboxes",
                    HttpStatus.CONFLICT);
        }
        WebhookInboxEntity inbox = inboxes.save(WebhookInboxEntity.builder()
                .token(secrets.randomToken(TOKEN_BYTES))
                .username(username)
                .createdAt(clock.instant())
                .build());
        return WebhookInboxDto.from(inbox);
    }

    @Transactional(readOnly = true)
    public List<WebhookInboxDto> list(String username) {
        return inboxes.findByUsernameOrderByIdAsc(username).stream().map(WebhookInboxDto::from).toList();
    }

    @Transactional
    public void delete(String username, String token) {
        WebhookInboxEntity inbox = owned(username, token);
        inboxRequests.deleteByInboxIds(List.of(inbox.getId()));
        inboxes.delete(inbox);
    }

    @Transactional(readOnly = true)
    public List<WebhookInboxRequestDto> requests(String username, String token) {
        WebhookInboxEntity inbox = owned(username, token);
        return inboxRequests.findByInboxIdOrderByIdDesc(inbox.getId()).stream()
                .limit(properties.getInboxRequestLimit())
                .map(request -> new WebhookInboxRequestDto(request.getId(), request.getReceivedAt(),
                        readHeaders(request.getHeaders()), request.getBody()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(String token, String username) {
        return inboxes.findByToken(token).filter(inbox -> inbox.getUsername().equals(username)).isPresent();
    }

    /**
     * Stores a delivered request when the inbox exists and belongs to {@code owner}, keeping
     * only the newest {@code webhooks.inbox-request-limit} requests. Returns false otherwise.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean receive(String token, String owner, Map<String, String> headers, String body) {
        Optional<WebhookInboxEntity> inbox = inboxes.findByToken(token)
                .filter(candidate -> candidate.getUsername().equals(owner));
        if (inbox.isEmpty()) {
            return false;
        }
        Long inboxId = inbox.get().getId();
        inboxRequests.save(WebhookInboxRequestEntity.builder()
                .inboxId(inboxId)
                .receivedAt(clock.instant())
                .headers(MAPPER.writeValueAsString(new LinkedHashMap<>(headers)))
                .body(limit(body))
                .build());
        List<WebhookInboxRequestEntity> kept = inboxRequests.findByInboxIdOrderByIdDesc(inboxId);
        if (kept.size() > properties.getInboxRequestLimit()) {
            inboxRequests.deleteAll(kept.subList(properties.getInboxRequestLimit(), kept.size()));
        }
        return true;
    }

    private WebhookInboxEntity owned(String username, String token) {
        return inboxes.findByToken(token)
                .filter(inbox -> inbox.getUsername().equals(username))
                .orElseThrow(() -> new CustomException(NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    static String limit(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BODY_BYTES) {
            return body;
        }
        String cut = new String(bytes, 0, MAX_BODY_BYTES, StandardCharsets.UTF_8);
        // A multi-byte character split at the limit decodes to U+FFFD; drop it so the result stays within the limit.
        while (cut.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    private static Map<String, String> readHeaders(String json) {
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode node = MAPPER.readTree(json);
        node.properties().forEach(entry -> headers.put(entry.getKey(), entry.getValue().asString()));
        return headers;
    }
}
