package com.awesome.testing.service.notification;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.wishlist.WishlistItemEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.wishlist.WishlistItemRepository;
import com.awesome.testing.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sends back-in-stock emails only after the business transaction has
 * committed, so a rolled-back stock change never notifies anyone. Shipment
 * and return emails are requested by warehouse-service through the internal
 * notifications endpoint and delivered by
 * {@link #onCustomerNotificationRequested(CustomerNotificationRequestedEvent)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceNotificationListener {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final CustomerEmailRenderer renderer;
    private final Clock commerceClock;

    @Value("${activemq.destination}")
    private String destination;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockReplenished(StockReplenishedEvent event) {
        ProductEntity product = productRepository.findById(event.productId()).orElse(null);
        if (product == null) {
            return;
        }
        List<WishlistItemEntity> watchers = wishlistItemRepository.findByProductIdAndNotifyWhenAvailableTrue(product.getId());
        LocalDateTime now = LocalDateTime.now(commerceClock);
        for (WishlistItemEntity watcher : watchers) {
            userRepository.findByUsername(watcher.getUsername()).ifPresent(user -> send(user,
                    renderer.render(user, EmailTemplate.BACK_IN_STOCK, Map.of(
                            "productId", String.valueOf(product.getId()),
                            "productName", product.getName())), null));
            watcher.setLastNotifiedAt(now);
        }
        wishlistItemRepository.saveAll(watchers);
    }

    // REQUIRES_NEW: after a commit the publisher's transaction is finished, so the email event
    // must be written in a transaction of its own (events published inside a transaction).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCustomerNotificationRequested(CustomerNotificationRequestedEvent event) {
        userRepository.findByUsername(event.username()).ifPresent(user -> send(user,
                renderer.render(user, event.template(), event.properties() == null ? Map.of() : event.properties()),
                event.requestId()));
    }

    private void send(UserEntity user, EmailDto email, java.util.UUID requestId) {
        try {
            emailService.sendEmail(email, destination, user, requestId);
        } catch (RuntimeException e) {
            log.error("Could not queue {} email for {}", email.getTemplate(), user.getUsername(), e);
        }
    }
}
