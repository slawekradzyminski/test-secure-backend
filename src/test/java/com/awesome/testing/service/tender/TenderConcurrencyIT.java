package com.awesome.testing.service.tender;

import com.awesome.testing.config.CommerceClockConfig;
import com.awesome.testing.service.reservation.OrderReservations;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.giftcard.GiftCardRepository;
import com.awesome.testing.repository.giftcard.GiftCardTransactionRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import com.awesome.testing.repository.tender.CartTenderRepository;
import com.awesome.testing.service.InventoryService;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyAccountCreator;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/** Two buyers holding the same gift card check out at the same time on PostgreSQL row locks. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({InventoryService.class, OrderService.class, CouponService.class, CommerceClockConfig.class, OrderReservations.class,
        CheckoutContributors.class, CommerceEvents.class, TenderCheckoutContributor.class, GiftCardService.class,
        LoyaltyService.class, LoyaltyAccountCreator.class, TenderOrderEvents.class, TenderRefundService.class})
class TenderConcurrencyIT {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private CartTenderRepository cartTenderRepository;
    @Autowired
    private GiftCardRepository giftCardRepository;
    @Autowired
    private GiftCardTransactionRepository giftCardTransactionRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCheckoutsSpendTheCardExactlyOnce() throws Exception {
        List<String> buyers = List.of("gift-buyer-one", "gift-buyer-two");
        GiftCardEntity card = prepare(buyers, new BigDecimal("50.00"), new BigDecimal("40.00"));
        CountDownLatch ready = new CountDownLatch(buyers.size());
        CountDownLatch start = new CountDownLatch(1);

        List<OrderDto> orders = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<OrderDto>> futures = buyers.stream()
                    .map(buyer -> executor.submit(() -> checkout(buyer, ready, start)))
                    .toList();
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            for (Future<OrderDto> future : futures) {
                orders.add(future.get(20, SECONDS));
            }
        }

        assertThat(orders).extracting(OrderDto::getGiftCardAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("40.00"), new BigDecimal("10.00"));
        assertThat(giftCardRepository.findById(card.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        List<GiftCardTransactionEntity> redemptions = giftCardTransactionRepository.findByGiftCardIdOrderByIdAsc(card.getId())
                .stream().filter(entry -> entry.getType() == GiftCardTransactionType.REDEEMED).toList();
        assertThat(redemptions).hasSize(2);
        assertThat(redemptions.stream().map(GiftCardTransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("-50.00");
        assertThat(redemptions.getLast().getBalanceAfter()).isEqualByComparingTo("0.00");
        assertThat(cartTenderRepository.findAllById(buyers)).isEmpty();
        cleanUp(card);
    }

    private GiftCardEntity prepare(List<String> buyers, BigDecimal cardAmount, BigDecimal price) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ProductEntity product = productRepository.save(ProductEntity.builder()
                    .name("Gift card product")
                    .description("Tender concurrency test")
                    .price(price)
                    .stockQuantity(10)
                    .category("Tests")
                    .build());
            GiftCardEntity card = giftCardRepository.save(GiftCardEntity.builder()
                    .code("GC-CONC-URRE-NT01")
                    .initialAmount(cardAmount)
                    .balance(cardAmount)
                    .createdBy("admin")
                    .build());
            for (String buyer : buyers) {
                cartItemRepository.save(CartItemEntity.builder()
                        .username(buyer).product(product).quantity(1).price(price).version(0L).build());
                cartTenderRepository.save(CartTenderEntity.builder().username(buyer).giftCardCode(card.getCode()).build());
            }
            return card;
        });
    }

    private OrderDto checkout(String buyer, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return orderService.createOrder(buyer, AddressDto.builder()
                    .street("1 Main St").city("Springfield").state("IL").zipCode("62701").country("US").build());
        } catch (CustomException exception) {
            throw new AssertionError("Checkout failed: " + exception.getMessage(), exception);
        }
    }

    private void cleanUp(GiftCardEntity card) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            giftCardTransactionRepository.deleteAll();
            giftCardRepository.deleteById(card.getId());
            movementRepository.deleteAll();
            orderRepository.deleteAll();
            productRepository.deleteAll();
        });
    }
}
