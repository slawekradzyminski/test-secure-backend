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
import java.util.LinkedHashMap;
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
            userRepository.findByUsername(watcher.getUsername()).ifPresent(user -> send(user, EmailDto.builder()
                    .to(user.getEmail())
                    .subject(product.getName() + " is back in stock")
                    .message("Hi " + user.getUsername() + ",\n\n" + product.getName()
                            + " from your wishlist is available again. Order soon, stock is limited.")
                    .template(EmailTemplate.BACK_IN_STOCK)
                    .properties(Map.of(
                            "username", user.getUsername(),
                            "productId", String.valueOf(product.getId()),
                            "productName", product.getName()))
                    .build()));
            watcher.setLastNotifiedAt(now);
        }
        wishlistItemRepository.saveAll(watchers);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCustomerNotificationRequested(CustomerNotificationRequestedEvent event) {
        userRepository.findByUsername(event.username()).ifPresent(user -> send(user, EmailDto.builder()
                .to(user.getEmail())
                .subject(event.subject())
                .message(event.message())
                .template(event.template())
                .properties(event.properties() == null ? Map.of() : new LinkedHashMap<>(event.properties()))
                .build()));
    }

    private void send(UserEntity user, EmailDto email) {
        try {
            emailService.sendEmail(email, destination, user);
        } catch (RuntimeException e) {
            log.error("Could not queue {} email for {}", email.getTemplate(), user.getUsername(), e);
        }
    }
}
