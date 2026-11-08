package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.fulfilment.CreateShipmentDto;
import com.awesome.testing.dto.fulfilment.PackShipmentDto;
import com.awesome.testing.dto.fulfilment.PickListDto;
import com.awesome.testing.dto.fulfilment.ShipShipmentDto;
import com.awesome.testing.dto.fulfilment.ShipmentDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.fulfilment.Carrier;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.repository.EmailEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseFlowTest extends AbstractCommerceFlowTest {

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void warehouseOperatorDrivesAnOrderFromQueueToDelivery() {
        ProductEntity product = product("25.00", 10);
        OrderDto order = order(clientToken, product, 2);
        markPaid(order.getId());

        PageDto<OrderDto> queue = executeGet(WAREHOUSE + "/queue", getHeadersWith(warehouseToken),
                new ParameterizedTypeReference<PageDto<OrderDto>>() {}).getBody();
        ShipmentDto created = executePost(WAREHOUSE + "/shipments", new CreateShipmentDto(order.getId()),
                getHeadersWith(warehouseToken), ShipmentDto.class).getBody();
        PickListDto pickList = executeGet(WAREHOUSE + "/shipments/" + created.getId() + "/pick-list",
                getHeadersWith(warehouseToken), PickListDto.class).getBody();
        transition(created.getId(), "pick", null);
        transition(created.getId(), "pack", new PackShipmentDto(new BigDecimal("2.40")));
        ShipmentDto shipped = transition(created.getId(), "ship", new ShipShipmentDto(Carrier.UPS, " 1z999aa10123456784 ")).getBody();
        OrderStatus afterShip = orderStatus(order.getId());
        ShipmentDto delivered = transition(created.getId(), "deliver", null).getBody();
        ShipmentDto tracked = executeGet(ORDERS_ENDPOINT + "/" + order.getId() + "/shipment",
                getHeadersWith(clientToken), ShipmentDto.class).getBody();

        assertThat(queue.getContent()).extracting(OrderDto::getId).containsExactly(order.getId());
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.AWAITING_PICK);
        assertThat(pickList.getTotalUnits()).isEqualTo(2);
        assertThat(shipped.getTrackingNumber()).isEqualTo("1Z999AA10123456784");
        assertThat(afterShip).isEqualTo(OrderStatus.SHIPPED);
        assertThat(delivered.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.DELIVERED);
        assertThat(tracked.getId()).isEqualTo(created.getId());
        assertThat(emailEventRepository.findAll()).extracting(event -> event.getType())
                .contains(EmailTemplate.ORDER_SHIPPED, EmailTemplate.ORDER_DELIVERED);
    }

    @Test
    void transitionsOutOfOrderAndInvalidTrackingNumbersAreRejected() {
        ShipmentDto shipment = releaseToWarehouse(order(clientToken, product("25.00", 10), 1).getId());

        ResponseEntity<ErrorDto> packBeforePick = executePost(WAREHOUSE + "/shipments/" + shipment.getId() + "/pack",
                new PackShipmentDto(new BigDecimal("1.00")), getHeadersWith(warehouseToken), ErrorDto.class);
        transition(shipment.getId(), "pick", null);
        ResponseEntity<String> tooHeavy = executePost(WAREHOUSE + "/shipments/" + shipment.getId() + "/pack",
                new PackShipmentDto(new BigDecimal("30.01")), getHeadersWith(warehouseToken), String.class);
        transition(shipment.getId(), "pack", new PackShipmentDto(new BigDecimal("1.00")));
        ResponseEntity<ErrorDto> badTracking = executePost(WAREHOUSE + "/shipments/" + shipment.getId() + "/ship",
                new ShipShipmentDto(Carrier.INPOST, "1234567890"), getHeadersWith(warehouseToken), ErrorDto.class);

        assertThat(packBeforePick.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(packBeforePick.getBody().getMessage()).isEqualTo("Cannot pack a shipment in status AWAITING_PICK");
        assertThat(tooHeavy.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badTracking.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badTracking.getBody().getMessage()).isEqualTo("Tracking number for INPOST must be 24 digits");
    }

    @Test
    void onlyPaidOrdersWithoutAShipmentCanBeReleased() {
        OrderDto pending = order(clientToken, product("25.00", 10), 1);

        ResponseEntity<ErrorDto> notPaid = executePost(WAREHOUSE + "/shipments", new CreateShipmentDto(pending.getId()),
                getHeadersWith(warehouseToken), ErrorDto.class);
        releaseToWarehouse(pending.getId());
        ResponseEntity<ErrorDto> twice = executePost(WAREHOUSE + "/shipments", new CreateShipmentDto(pending.getId()),
                getHeadersWith(warehouseToken), ErrorDto.class);

        assertThat(notPaid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(twice.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void customerCancellationIsAllowedBeforePickingAndBlockedAfterwards() {
        ProductEntity product = product("25.00", 10);
        OrderDto early = order(clientToken, product, 1);
        ShipmentDto earlyShipment = releaseToWarehouse(early.getId());
        OrderDto late = order(clientToken, product, 1);
        ShipmentDto lateShipment = releaseToWarehouse(late.getId());
        transition(lateShipment.getId(), "pick", null);

        ResponseEntity<OrderDto> cancelEarly = executePost(ORDERS_ENDPOINT + "/" + early.getId() + "/cancel", null,
                getHeadersWith(clientToken), OrderDto.class);
        ResponseEntity<ErrorDto> cancelLate = executePost(ORDERS_ENDPOINT + "/" + late.getId() + "/cancel", null,
                getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ShipmentDto> warehouseCancel = transition(lateShipment.getId(), "cancel", null);

        assertThat(cancelEarly.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shipmentStatus(earlyShipment.getId())).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(cancelLate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(warehouseCancel.getBody().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(orderStatus(late.getId())).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void legacyStatusEndpointCannotBypassAnActiveShipment() {
        ShipmentDto shipment = releaseToWarehouse(order(clientToken, product("25.00", 10), 1).getId());

        ResponseEntity<ErrorDto> manualShip = executePut(ORDERS_ENDPOINT + "/" + shipment.getOrderId() + "/status",
                OrderStatus.SHIPPED, getHeadersWith(adminToken), ErrorDto.class);

        assertThat(manualShip.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderStatus(shipment.getOrderId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void clientsCannotUseWarehouseEndpointsOrTrackOtherPeoplesOrders() {
        ShipmentDto shipment = releaseToWarehouse(order(clientToken, product("25.00", 10), 1).getId());

        ResponseEntity<String> clientQueue = executeGet(WAREHOUSE + "/queue", getHeadersWith(clientToken), String.class);
        ResponseEntity<String> clientPick = executePost(WAREHOUSE + "/shipments/" + shipment.getId() + "/pick", null,
                getHeadersWith(clientToken), String.class);
        ResponseEntity<String> strangerTracking = executeGet(ORDERS_ENDPOINT + "/" + shipment.getOrderId() + "/shipment",
                getHeadersWith(otherClientToken), String.class);
        ResponseEntity<String> adminQueue = executeGet(WAREHOUSE + "/queue", getHeadersWith(adminToken), String.class);

        assertThat(clientQueue.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(clientPick.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(strangerTracking.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(adminQueue.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private ShipmentStatus shipmentStatus(Long shipmentId) {
        return executeGet(WAREHOUSE + "/shipments/" + shipmentId, getHeadersWith(warehouseToken), ShipmentDto.class)
                .getBody().getStatus();
    }
}
