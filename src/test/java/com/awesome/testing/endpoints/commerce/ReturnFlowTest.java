package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.returns.CreateReturnDto;
import com.awesome.testing.dto.returns.ReceiveItemDto;
import com.awesome.testing.dto.returns.ReceiveReturnDto;
import com.awesome.testing.dto.returns.RejectReturnDto;
import com.awesome.testing.dto.returns.ReturnDto;
import com.awesome.testing.dto.returns.ReturnItemRequestDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.entity.returns.ItemCondition;
import com.awesome.testing.entity.returns.ReturnReason;
import com.awesome.testing.entity.returns.ReturnStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnFlowTest extends AbstractCommerceFlowTest {

    private static final String ADMIN_RETURNS = "/api/v1/admin/returns";

    @Test
    void returnOfADiscountedOrderRestocksResellableItemsAndRefundsProportionally() {
        executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code("TENOFF").type(CouponType.PERCENTAGE)
                .value(BigDecimal.TEN).build(), getHeadersWith(adminToken), String.class);
        ProductEntity phone = product("100.00", 10);
        ProductEntity cable = product("20.00", 10);
        addToCart(clientToken, phone, 1);
        addToCart(clientToken, cable, 2);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto("TENOFF"), getHeadersWith(clientToken), CartDto.class);
        OrderDto order = checkout(clientToken);
        deliver(order.getId());

        ReturnDto requested = requestReturn(order, List.of(item(phone, 1), item(cable, 1)), ReturnDto.class).getBody();
        executePost(ADMIN_RETURNS + "/" + requested.getId() + "/approve", null, getHeadersWith(adminToken), ReturnDto.class);
        ReturnDto received = executePost(ADMIN_RETURNS + "/" + requested.getId() + "/receive",
                new ReceiveReturnDto(List.of(new ReceiveItemDto(phone.getId(), ItemCondition.RESELLABLE),
                        new ReceiveItemDto(cable.getId(), ItemCondition.DAMAGED))),
                getHeadersWith(adminToken), ReturnDto.class).getBody();
        ReturnDto refunded = executePost(ADMIN_RETURNS + "/" + requested.getId() + "/refund", null,
                getHeadersWith(adminToken), ReturnDto.class).getBody();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("126.00");
        assertThat(requested.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(requested.getEstimatedRefund()).isEqualByComparingTo("108.00");
        assertThat(received.getStatus()).isEqualTo(ReturnStatus.RECEIVED);
        assertThat(stockOf(phone)).isEqualTo(10);
        assertThat(stockOf(cable)).isEqualTo(8);
        assertThat(refunded.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(refunded.getRefundAmount()).isEqualByComparingTo("108.00");
    }

    @Test
    void returnsRequireADeliveredOrderAndRespectReturnableQuantities() {
        ProductEntity product = product("10.00", 10);
        OrderDto order = order(clientToken, product, 2);

        ResponseEntity<ErrorDto> notDelivered = requestReturn(order, List.of(item(product, 1)), ErrorDto.class);
        deliver(order.getId());
        ResponseEntity<ReturnDto> first = requestReturn(order, List.of(item(product, 2)), ReturnDto.class);
        ResponseEntity<ErrorDto> tooMany = requestReturn(order, List.of(item(product, 1)), ErrorDto.class);
        executePost(ADMIN_RETURNS + "/" + first.getBody().getId() + "/reject", new RejectReturnDto("Missing packaging"),
                getHeadersWith(adminToken), ReturnDto.class);
        ResponseEntity<ReturnDto> afterRejection = requestReturn(order, List.of(item(product, 2)), ReturnDto.class);

        assertThat(notDelivered.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMany.getBody().getMessage())
                .isEqualTo("Requested quantity for product " + product.getId() + " exceeds the returnable quantity of 0");
        assertThat(afterRejection.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void customersCannotReturnOtherPeoplesOrdersOrProductsTheyDidNotBuy() {
        ProductEntity bought = product("10.00", 10);
        ProductEntity notBought = product("10.00", 10);
        OrderDto order = order(clientToken, bought, 1);
        deliver(order.getId());

        ResponseEntity<ErrorDto> stranger = executePost(ORDERS_ENDPOINT + "/" + order.getId() + "/returns",
                new CreateReturnDto(ReturnReason.DAMAGED, null, List.of(item(bought, 1))),
                getHeadersWith(otherClientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> wrongProduct = requestReturn(order, List.of(item(notBought, 1)), ErrorDto.class);

        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wrongProduct.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminTransitionsFollowTheStateMachine() {
        ProductEntity product = product("10.00", 10);
        OrderDto order = order(clientToken, product, 1);
        deliver(order.getId());
        ReturnDto requested = requestReturn(order, List.of(item(product, 1)), ReturnDto.class).getBody();

        ResponseEntity<ErrorDto> refundTooEarly = executePost(ADMIN_RETURNS + "/" + requested.getId() + "/refund", null,
                getHeadersWith(adminToken), ErrorDto.class);
        executePost(ADMIN_RETURNS + "/" + requested.getId() + "/approve", null, getHeadersWith(adminToken), ReturnDto.class);
        ResponseEntity<ErrorDto> incompleteInspection = executePost(ADMIN_RETURNS + "/" + requested.getId() + "/receive",
                new ReceiveReturnDto(List.of(new ReceiveItemDto(product.getId() + 999, ItemCondition.RESELLABLE))),
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> rejectApproved = executePost(ADMIN_RETURNS + "/" + requested.getId() + "/reject",
                new RejectReturnDto("Too late"), getHeadersWith(adminToken), ErrorDto.class);

        assertThat(refundTooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(incompleteInspection.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejectApproved.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(executeGet("/api/v1/returns/" + requested.getId(), getHeadersWith(otherClientToken), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ReturnItemRequestDto item(ProductEntity product, int quantity) {
        return new ReturnItemRequestDto(product.getId(), quantity);
    }

    private <T> ResponseEntity<T> requestReturn(OrderDto order, List<ReturnItemRequestDto> items, Class<T> type) {
        return executePost(ORDERS_ENDPOINT + "/" + order.getId() + "/returns",
                new CreateReturnDto(ReturnReason.NOT_AS_DESCRIBED, "Not what I expected", items),
                getHeadersWith(clientToken), type);
    }
}
