package com.awesome.testing.service.notification;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.fulfilment.ShipmentEntity;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.entity.returns.ReturnRequestEntity;
import com.awesome.testing.entity.wishlist.WishlistItemEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.repository.returns.ReturnRequestRepository;
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
 * Sends customer emails for commerce events only after the business
 * transaction has committed, so a rolled-back change never notifies anyone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceNotificationListener {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) {
        ShipmentEntity shipment = shipmentRepository.findById(event.shipmentId()).orElse(null);
        if (shipment == null) {
            return;
        }
        OrderEntity order = shipment.getOrder();
        userRepository.findByUsername(order.getUsername()).ifPresent(user -> {
            if (event.status() == ShipmentStatus.SHIPPED) {
                send(user, EmailDto.builder()
                        .to(user.getEmail())
                        .subject("Order #" + order.getId() + " has shipped")
                        .message("Hi " + user.getUsername() + ",\n\nYour order #" + order.getId()
                                + " is on its way with " + shipment.getCarrier() + ". Tracking number: "
                                + shipment.getTrackingNumber() + ".")
                        .template(EmailTemplate.ORDER_SHIPPED)
                        .properties(Map.of(
                                "username", user.getUsername(),
                                "orderId", String.valueOf(order.getId()),
                                "carrier", String.valueOf(shipment.getCarrier()),
                                "trackingNumber", String.valueOf(shipment.getTrackingNumber())))
                        .build());
            } else if (event.status() == ShipmentStatus.DELIVERED) {
                send(user, EmailDto.builder()
                        .to(user.getEmail())
                        .subject("Order #" + order.getId() + " was delivered")
                        .message("Hi " + user.getUsername() + ",\n\nYour order #" + order.getId()
                                + " was delivered. You can now review the products and request a return within 30 days.")
                        .template(EmailTemplate.ORDER_DELIVERED)
                        .properties(Map.of(
                                "username", user.getUsername(),
                                "orderId", String.valueOf(order.getId())))
                        .build());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnStatusChanged(ReturnStatusChangedEvent event) {
        ReturnRequestEntity request = returnRequestRepository.findById(event.returnId()).orElse(null);
        if (request == null) {
            return;
        }
        userRepository.findByUsername(request.getUsername()).ifPresent(user -> {
            String refund = request.getRefundAmount() == null ? "" : " Refund: " + request.getRefundAmount() + ".";
            String note = request.getResolutionNote() == null ? "" : " Note: " + request.getResolutionNote();
            send(user, EmailDto.builder()
                    .to(user.getEmail())
                    .subject("Return #" + request.getId() + " is " + request.getStatus())
                    .message("Hi " + user.getUsername() + ",\n\nYour return #" + request.getId() + " for order #"
                            + request.getOrder().getId() + " is now " + request.getStatus() + "." + refund + note)
                    .template(EmailTemplate.RETURN_UPDATED)
                    .properties(Map.of(
                            "username", user.getUsername(),
                            "returnId", String.valueOf(request.getId()),
                            "status", request.getStatus().name()))
                    .build());
        });
    }

    private void send(UserEntity user, EmailDto email) {
        try {
            emailService.sendEmail(email, destination, user);
        } catch (RuntimeException e) {
            log.error("Could not queue {} email for {}", email.getTemplate(), user.getUsername(), e);
        }
    }
}
