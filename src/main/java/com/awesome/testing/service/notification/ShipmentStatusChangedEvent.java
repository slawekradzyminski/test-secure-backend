package com.awesome.testing.service.notification;

import com.awesome.testing.entity.fulfilment.ShipmentStatus;

/** Published after a shipment reaches SHIPPED or DELIVERED. */
public record ShipmentStatusChangedEvent(Long shipmentId, ShipmentStatus status) {
}
