package edu.cit.balacy.events;

import java.util.List;

/** Published by the Order module when an order is CANCELLED and its items restocked. */
public record OrderCancelledEvent(
        Long orderId,
        List<OrderPlacedEvent.Line> restoredItems
) {
}
