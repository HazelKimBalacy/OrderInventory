package edu.cit.balacy.events;

/**
 * Published by the Inventory module after a successful reserve() leaves a
 * product below the configured threshold. Separate from the order lifecycle
 * events on purpose: it's an inventory-domain fact ("this product needs
 * reordering"), not an order-domain one, and the Notification module logs it
 * as its own entry type.
 */
public record LowStockEvent(
        String productId,
        String name,
        int remainingStock,
        int threshold
) {
}
