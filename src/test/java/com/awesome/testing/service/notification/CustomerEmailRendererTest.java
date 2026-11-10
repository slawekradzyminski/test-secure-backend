package com.awesome.testing.service.notification;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerEmailRendererTest {

    private final CustomerEmailRenderer renderer = new CustomerEmailRenderer();
    private final UserEntity user = UserEntity.builder().username("buyer").email("buyer@example.com").password("secret-password").build();

    @Test
    void rendersShipmentEmails() {
        EmailDto email = renderer.render(user, EmailTemplate.ORDER_SHIPPED,
                Map.of("orderId", "7", "carrier", "DPD", "trackingNumber", "12345678901234"));

        assertThat(email.getTo()).isEqualTo("buyer@example.com");
        assertThat(email.getSubject()).isEqualTo("Order #7 has shipped");
        assertThat(email.getMessage()).isEqualTo(
                "Hi buyer,\n\nYour order #7 is on its way with DPD. Tracking number: 12345678901234.");
        assertThat(email.getTemplate()).isEqualTo(EmailTemplate.ORDER_SHIPPED);
        assertThat(email.getProperties()).containsEntry("username", "buyer").containsEntry("orderId", "7");
    }

    @Test
    void rendersDeliveryEmails() {
        EmailDto email = renderer.render(user, EmailTemplate.ORDER_DELIVERED, Map.of("orderId", "7"));

        assertThat(email.getSubject()).isEqualTo("Order #7 was delivered");
        assertThat(email.getMessage()).isEqualTo("Hi buyer,\n\nYour order #7 was delivered. "
                + "You can now review the products and request a return within 30 days.");
    }

    @Test
    void rendersReturnUpdatesWithOptionalRefundAndNote() {
        EmailDto plain = renderer.render(user, EmailTemplate.RETURN_UPDATED,
                Map.of("returnId", "3", "orderId", "7", "status", "APPROVED"));
        EmailDto refunded = renderer.render(user, EmailTemplate.RETURN_UPDATED,
                Map.of("returnId", "3", "orderId", "7", "status", "REFUNDED", "refundAmount", "19.99", "note", "Thanks"));

        assertThat(plain.getSubject()).isEqualTo("Return #3 is APPROVED");
        assertThat(plain.getMessage()).isEqualTo("Hi buyer,\n\nYour return #3 for order #7 is now APPROVED.");
        assertThat(refunded.getMessage())
                .isEqualTo("Hi buyer,\n\nYour return #3 for order #7 is now REFUNDED. Refund: 19.99. Note: Thanks");
    }

    @Test
    void rendersBackInStockEmails() {
        EmailDto email = renderer.render(user, EmailTemplate.BACK_IN_STOCK, Map.of("productId", "4", "productName", "Lamp"));

        assertThat(email.getSubject()).isEqualTo("Lamp is back in stock");
        assertThat(email.getMessage())
                .isEqualTo("Hi buyer,\n\nLamp from your wishlist is available again. Order soon, stock is limited.");
    }

    @Test
    void rendersPaymentEmailsWithTheDefaultCurrencyAndOptionalFailureMessage() {
        EmailDto received = renderer.render(user, EmailTemplate.PAYMENT_RECEIVED, Map.of("orderId", "7", "amount", "49.99"));
        EmailDto receivedEur = renderer.render(user, EmailTemplate.PAYMENT_RECEIVED,
                Map.of("orderId", "7", "amount", "45.00", "currency", "EUR"));
        EmailDto declined = renderer.render(user, EmailTemplate.PAYMENT_FAILED,
                Map.of("orderId", "7", "failureCode", "CARD_DECLINED"));
        EmailDto explained = renderer.render(user, EmailTemplate.PAYMENT_FAILED,
                Map.of("orderId", "7", "failureCode", "CARD_DECLINED", "failureMessage", "Your card was declined"));

        assertThat(received.getSubject()).isEqualTo("Payment received for order #7");
        assertThat(received.getMessage()).isEqualTo("Hi buyer,\n\nWe received your payment of 49.99 USD for order #7. "
                + "We will let you know when it ships.");
        assertThat(received.getTemplate()).isEqualTo(EmailTemplate.PAYMENT_RECEIVED);
        assertThat(receivedEur.getMessage()).contains("payment of 45.00 EUR for order #7");
        assertThat(declined.getSubject()).isEqualTo("Payment for order #7 failed");
        assertThat(declined.getMessage()).isEqualTo("Hi buyer,\n\nThe payment for order #7 failed: CARD_DECLINED. "
                + "Your order is still waiting for payment, so you can try again with another card.");
        assertThat(explained.getMessage()).contains("failed: Your card was declined.");
    }

    @Test
    void rendersCarrierTrackingEmails() {
        Map<String, String> tracking = Map.of("orderId", "7", "carrier", "DPD", "trackingNumber", "12345678901234");

        EmailDto out = renderer.render(user, EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, tracking);
        EmailDto failed = renderer.render(user, EmailTemplate.SHIPMENT_DELIVERY_FAILED, tracking);

        assertThat(out.getSubject()).isEqualTo("Order #7 is out for delivery");
        assertThat(out.getMessage()).isEqualTo(
                "Hi buyer,\n\nYour order #7 is out for delivery with DPD today. Tracking number: 12345678901234.");
        assertThat(failed.getSubject()).isEqualTo("Delivery of order #7 failed");
        assertThat(failed.getMessage()).isEqualTo("Hi buyer,\n\nDPD could not deliver your order #7. "
                + "The courier will try again. Tracking number: 12345678901234.");
    }

    @Test
    void rendersLoyaltyAndGiftCardEmails() {
        EmailDto points = renderer.render(user, EmailTemplate.LOYALTY_POINTS_EARNED,
                Map.of("points", "120", "balance", "450", "orderId", "7"));
        EmailDto bonus = renderer.render(user, EmailTemplate.LOYALTY_POINTS_EARNED, Map.of("points", "50", "balance", "50"));
        EmailDto giftCard = renderer.render(user, EmailTemplate.GIFT_CARD_ISSUED, Map.of("code", "GC-ABCD-1234", "amount", "50.00"));
        EmailDto euroCard = renderer.render(user, EmailTemplate.GIFT_CARD_ISSUED,
                Map.of("code", "GC-ABCD-1234", "amount", "20.00", "currency", "EUR"));

        assertThat(points.getSubject()).isEqualTo("You earned 120 loyalty points");
        assertThat(points.getMessage())
                .isEqualTo("Hi buyer,\n\nYou earned 120 loyalty points for order #7. Your balance is now 450 points.");
        assertThat(bonus.getMessage()).isEqualTo("Hi buyer,\n\nYou earned 50 loyalty points. Your balance is now 50 points.");
        assertThat(giftCard.getSubject()).isEqualTo("Your gift card is ready");
        assertThat(giftCard.getMessage()).isEqualTo("Hi buyer,\n\nA gift card worth 50.00 USD was issued to you. "
                + "Code: GC-ABCD-1234. Enter it at checkout to use it.");
        assertThat(euroCard.getMessage()).contains("worth 20.00 EUR");
    }

    @ParameterizedTest
    @MethodSource("requiredProperties")
    void everyRequiredPropertyOfTheNewTemplatesIsValidated(EmailTemplate template, String property) {
        Map<String, String> complete = new HashMap<>();
        CustomerEmailRenderer.REQUIRED_PROPERTIES.get(template).forEach(name -> complete.put(name, "x"));
        complete.put(property, " ");

        assertThatThrownBy(() -> renderer.validate(template, complete))
                .isInstanceOf(CustomException.class)
                .hasMessage("properties." + property + " is required for " + template);
        complete.remove(property);
        assertThatThrownBy(() -> renderer.validate(template, complete)).isInstanceOf(CustomException.class);
    }

    static Stream<Arguments> requiredProperties() {
        return Stream.of(
                Arguments.of(EmailTemplate.PAYMENT_RECEIVED, "orderId"),
                Arguments.of(EmailTemplate.PAYMENT_RECEIVED, "amount"),
                Arguments.of(EmailTemplate.PAYMENT_FAILED, "orderId"),
                Arguments.of(EmailTemplate.PAYMENT_FAILED, "failureCode"),
                Arguments.of(EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, "orderId"),
                Arguments.of(EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, "carrier"),
                Arguments.of(EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, "trackingNumber"),
                Arguments.of(EmailTemplate.SHIPMENT_DELIVERY_FAILED, "orderId"),
                Arguments.of(EmailTemplate.SHIPMENT_DELIVERY_FAILED, "carrier"),
                Arguments.of(EmailTemplate.SHIPMENT_DELIVERY_FAILED, "trackingNumber"),
                Arguments.of(EmailTemplate.LOYALTY_POINTS_EARNED, "points"),
                Arguments.of(EmailTemplate.LOYALTY_POINTS_EARNED, "balance"),
                Arguments.of(EmailTemplate.GIFT_CARD_ISSUED, "code"),
                Arguments.of(EmailTemplate.GIFT_CARD_ISSUED, "amount"));
    }

    @Test
    void rejectsMissingPropertiesAndTemplatesItDoesNotOwn() {
        assertThatThrownBy(() -> renderer.render(user, EmailTemplate.ORDER_DELIVERED, Map.of("orderId", " ")))
                .isInstanceOf(CustomException.class)
                .hasMessage("properties.orderId is required for ORDER_DELIVERED");
        assertThatThrownBy(() -> renderer.validate(EmailTemplate.PASSWORD_RESET_REQUESTED, Map.of()))
                .isInstanceOf(CustomException.class)
                .hasMessage("No customer email template PASSWORD_RESET_REQUESTED");
    }
}
