package edu.cit.balacy.inventory;

/** Read-only view of an InventoryItem, safe to serialize back to the client. */
public record InventoryItemDTO(String productId, String name, int stock) {

    public static InventoryItemDTO from(InventoryItem item) {
        return new InventoryItemDTO(item.getProductId(), item.getName(), item.getStock());
    }
}
