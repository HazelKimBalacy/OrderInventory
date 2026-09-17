package edu.cit.balacy.shop;

import edu.cit.balacy.inventory.InventoryItemDTO;

import java.util.List;

/**
 * Shape required by the spec: { status, reason, items: [{ productId, outcome }], inventory }.
 *
 * {@code inventory} is the post-operation state of the products this order
 * touched, so the client can show the effect immediately (the dashboard also
 * re-fetches GET /api/inventory).
 */
public record OrderResponse(
        Long orderId,
        OrderStatus status,
        String reason,
        List<ItemOutcome> items,
        List<InventoryItemDTO> inventory
) {
    public record ItemOutcome(String productId, int quantity, String outcome) {
    }
}
