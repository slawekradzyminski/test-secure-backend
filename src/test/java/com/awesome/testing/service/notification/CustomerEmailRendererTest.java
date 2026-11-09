package com.awesome.testing.service.notification;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    void rejectsMissingPropertiesAndTemplatesItDoesNotOwn() {
        assertThatThrownBy(() -> renderer.render(user, EmailTemplate.ORDER_DELIVERED, Map.of("orderId", " ")))
                .isInstanceOf(CustomException.class)
                .hasMessage("properties.orderId is required for ORDER_DELIVERED");
        assertThatThrownBy(() -> renderer.validate(EmailTemplate.PASSWORD_RESET_REQUESTED, Map.of()))
                .isInstanceOf(CustomException.class)
                .hasMessage("No customer email template PASSWORD_RESET_REQUESTED");
    }
}
