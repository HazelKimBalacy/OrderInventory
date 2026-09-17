package edu.cit.balacy.events;

import java.util.List;

/**
 * Published by the Order module when an order is CONFIRMED.
 *
 * Lives in a neutral edu.cit.balacy.events package on purpose. If the event
 * classes lived in .shop, then .inventory would have to import .shop to
 * publish LowStockEvent, and Notification would be coupled to the Order
 * module's package rather than to the contract. A shared, dependency-free
 * event package keeps the arrows pointing one way: shop -> events,
 * inventory -> events, notification -> events, and nothing back.
 */
public record OrderPlacedEvent(
        Long orderId,
        List<Line> items
) {
    public record Line(String productId, int quantity) {
    }
}
