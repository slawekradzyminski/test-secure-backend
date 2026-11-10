package com.awesome.testing.service.webhook;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.dto.webhook.WebhookUpdateDto;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import com.awesome.testing.entity.webhook.WebhookInboxEntity;
import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.awesome.testing.repository.webhook.WebhookDeliveryRepository;
import com.awesome.testing.repository.webhook.WebhookInboxRepository;
import com.awesome.testing.repository.webhook.WebhookInboxRequestRepository;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Webhook subscriptions of the calling user: CRUD, secrets, test pings and the delivery log. */
@Service
public class WebhookService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookInboxRepository inboxes;
    private final WebhookInboxRequestRepository inboxRequests;
    private final WebhookInboxService inboxService;
    private final WebhookUrlPolicy urlPolicy;
    private final WebhookSecrets secrets;
    private final WebhookDeliveryLog deliveryLog;
    private final WebhookDispatcher dispatcher;
    private final WebhookProperties properties;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public WebhookService(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries,
                          WebhookInboxRepository inboxes, WebhookInboxRequestRepository inboxRequests,
                          WebhookInboxService inboxService, WebhookUrlPolicy urlPolicy, WebhookSecrets secrets,
                          WebhookDeliveryLog deliveryLog, WebhookDispatcher dispatcher, WebhookProperties properties,
                          Clock clock) {
        this.subscriptions = subscriptions;
        this.deliveryRepository = deliveries;
        this.inboxes = inboxes;
        this.inboxRequests = inboxRequests;
        this.inboxService = inboxService;
        this.urlPolicy = urlPolicy;
        this.secrets = secrets;
        this.deliveryLog = deliveryLog;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionDto> list(String username) {
        return subscriptions.findByUsernameOrderByIdAsc(username).stream().map(WebhookSubscriptionDto::from).toList();
    }

    @Transactional
    public WebhookSubscriptionDto create(String username, WebhookCreateDto request) {
        if (subscriptions.countByUsername(username) >= properties.getMaxSubscriptionsPerUser()) {
            throw new CustomException("You can have at most " + properties.getMaxSubscriptionsPerUser()
                    + " webhook subscriptions", HttpStatus.CONFLICT);
        }
        List<String> events = events(request.getEvents());
        String url = vettedUrl(username, request.getUrl());
        String secret = secrets.generate();
        Instant now = clock.instant();
        WebhookSubscriptionEntity subscription = WebhookSubscriptionEntity.builder()
                .username(username)
                .url(url)
                .description(request.getDescription())
                .secret(secrets.protect(secret))
                .active(true)
                .consecutiveFailures(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        subscription.setEventList(events);
        return WebhookSubscriptionDto.from(subscriptions.save(subscription), secret);
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionDto get(String username, Long id) {
        return WebhookSubscriptionDto.from(owned(username, id));
    }

    @Transactional
    public WebhookSubscriptionDto update(String username, Long id, WebhookUpdateDto request) {
        WebhookSubscriptionEntity subscription = owned(username, id);
        List<String> events = events(request.getEvents());
        subscription.setUrl(vettedUrl(username, request.getUrl()));
        subscription.setEventList(events);
        subscription.setDescription(request.getDescription());
        if (Boolean.TRUE.equals(request.getActive()) && !subscription.isActive()) {
            subscription.setActive(true);
            subscription.setConsecutiveFailures(0);
            subscription.setDisabledReason(null);
        } else if (Boolean.FALSE.equals(request.getActive())) {
            subscription.setActive(false);
        }
        subscription.setUpdatedAt(clock.instant());
        return WebhookSubscriptionDto.from(subscription);
    }

    @Transactional
    public void delete(String username, Long id) {
        WebhookSubscriptionEntity subscription = owned(username, id);
        deliveryRepository.deleteBySubscriptionIds(List.of(subscription.getId()));
        subscriptions.delete(subscription);
    }

    @Transactional
    public WebhookSubscriptionDto rotateSecret(String username, Long id) {
        WebhookSubscriptionEntity subscription = owned(username, id);
        String secret = secrets.generate();
        subscription.setSecret(secrets.protect(secret));
        subscription.setUpdatedAt(clock.instant());
        return WebhookSubscriptionDto.from(subscription, secret);
    }

    /** Sends a {@code webhook.ping} now and returns the recorded delivery. */
    public WebhookDeliveryDto test(String username, Long id) {
        WebhookSubscriptionEntity subscription = activeOwned(username, id);
        return sendNow(deliveryLog.recordPing(subscription));
    }

    /** Sends the event of an earlier delivery again as a new delivery and returns it. */
    public WebhookDeliveryDto redeliver(String username, Long id, UUID deliveryId) {
        activeOwned(username, id);
        WebhookDeliveryEntity original = ownedDelivery(id, deliveryId);
        return sendNow(deliveryLog.recordRedelivery(original));
    }

    @Transactional(readOnly = true)
    public PageDto<WebhookDeliveryDto> deliveries(String username, Long id, WebhookDeliveryStatus status,
                                                  int page, int size) {
        owned(username, id);
        Pageable pageable = page(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<WebhookDeliveryEntity> result = status == null
                ? deliveryRepository.findBySubscriptionId(id, pageable)
                : deliveryRepository.findBySubscriptionIdAndStatus(id, status, pageable);
        return PageDto.from(result.map(WebhookDeliveryDto::from));
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryDto delivery(String username, Long id, UUID deliveryId) {
        owned(username, id);
        return WebhookDeliveryDto.from(ownedDelivery(id, deliveryId));
    }

    @Transactional(readOnly = true)
    public PageDto<WebhookSubscriptionDto> adminList(String username, int page, int size) {
        Pageable pageable = page(page, size, Sort.by("id"));
        Page<WebhookSubscriptionEntity> result = username == null || username.isBlank()
                ? subscriptions.findAll(pageable)
                : subscriptions.findByUsername(username.trim(), pageable);
        return PageDto.from(result.map(WebhookSubscriptionDto::from));
    }

    /** Removes every subscription, delivery, inbox and inbox request of a deleted user. */
    @Transactional
    public void deleteForUser(String username) {
        List<WebhookSubscriptionEntity> owned = subscriptions.findByUsernameOrderByIdAsc(username);
        if (!owned.isEmpty()) {
            deliveryRepository.deleteBySubscriptionIds(owned.stream().map(WebhookSubscriptionEntity::getId).toList());
            subscriptions.deleteAll(owned);
        }
        List<WebhookInboxEntity> userInboxes = inboxes.findByUsernameOrderByIdAsc(username);
        if (!userInboxes.isEmpty()) {
            inboxRequests.deleteByInboxIds(userInboxes.stream().map(WebhookInboxEntity::getId).toList());
            inboxes.deleteAll(userInboxes);
        }
    }

    private WebhookDeliveryDto sendNow(WebhookDeliveryEntity delivery) {
        Optional<WebhookDeliveryEntity> attempted = dispatcher.dispatch(delivery.getId());
        return WebhookDeliveryDto.from(attempted.orElseGet(() -> deliveryRepository.findById(delivery.getId()).orElseThrow()));
    }

    private WebhookSubscriptionEntity owned(String username, Long id) {
        WebhookSubscriptionEntity subscription = subscriptions.findById(id)
                .orElseThrow(() -> new CustomException("Webhook subscription not found", HttpStatus.NOT_FOUND));
        if (!subscription.getUsername().equals(username)) {
            throw new CustomException("You cannot access someone else's webhook subscription", HttpStatus.FORBIDDEN);
        }
        return subscription;
    }

    private WebhookSubscriptionEntity activeOwned(String username, Long id) {
        WebhookSubscriptionEntity subscription = owned(username, id);
        if (!subscription.isActive()) {
            throw new CustomException("Webhook subscription is disabled; enable it with PUT first", HttpStatus.CONFLICT);
        }
        return subscription;
    }

    private WebhookDeliveryEntity ownedDelivery(Long subscriptionId, UUID deliveryId) {
        return deliveryRepository.findByIdAndSubscriptionId(deliveryId, subscriptionId)
                .orElseThrow(() -> new CustomException("Webhook delivery not found", HttpStatus.NOT_FOUND));
    }

    private static List<String> events(List<String> requested) {
        try {
            return WebhookEventTypes.normalize(requested);
        } catch (IllegalArgumentException e) {
            throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }
    }

    private String vettedUrl(String username, String url) {
        String trimmed = url.trim();
        Optional<String> inboxToken = WebhookInboxService.token(trimmed);
        if (inboxToken.isPresent()) {
            if (!inboxService.isOwnedBy(inboxToken.get(), username)) {
                throw new CustomException("Webhook URL rejected: unknown inbox; create one with POST /api/v1/webhook-inboxes",
                        HttpStatus.BAD_REQUEST);
            }
            return trimmed;
        }
        try {
            urlPolicy.check(trimmed);
            return trimmed;
        } catch (WebhookUrlRejectedException e) {
            throw new CustomException("Webhook URL rejected: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }
    }

    private static Pageable page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
    }
}
