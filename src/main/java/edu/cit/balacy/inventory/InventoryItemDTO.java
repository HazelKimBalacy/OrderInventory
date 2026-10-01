package edu.cit.balacy.inventory;

/**
 * Read-only view of an InventoryItem, safe to serialize back to the client.
 *
 * {@code lowStock} is computed here rather than in the frontend so the
 * threshold stays a single backend concern (app.inventory.low-stock-threshold)
 * - the UI just styles the flag it's given instead of hardcoding 5.
 */
public record InventoryItemDTO(String productId, String name, int stock, boolean lowStock) {

    static InventoryItemDTO from(InventoryItem item, int threshold) {
        return new InventoryItemDTO(
                item.getProductId(), item.getName(), item.getStock(), item.getStock() < threshold);
    }
}
