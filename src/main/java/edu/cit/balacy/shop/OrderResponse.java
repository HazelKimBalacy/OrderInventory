package edu.cit.balacy.shop;

import edu.cit.balacy.inventory.InventoryItemDTO;

/** Shape required by the spec: { status, reason, inventory }. */
public record OrderResponse(
        OrderStatus status,
        String reason,
        InventoryItemDTO inventory
) {
}
