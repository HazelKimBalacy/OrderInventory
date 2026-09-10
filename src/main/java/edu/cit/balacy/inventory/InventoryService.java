package edu.cit.balacy.inventory;

/**
 * Public contract for the Inventory module. This is the ONLY inventory type
 * (besides its exceptions and the read-only InventoryItem it returns) that
 * other modules, such as edu.cit.balacy.shop, are allowed to reference.
 * The implementation is intentionally package-private so nothing outside
 * this package can new it up, cast to it, or reach around the interface.
 */
public interface InventoryService {

    /**
     * @throws ProductNotFoundException if productId does not exist
     */
    InventoryItem getItem(String productId);

    /**
     * Atomically checks and decrements stock for productId by quantity.
     *
     * @return the InventoryItem reflecting the post-reservation stock level
     * @throws ProductNotFoundException     if productId does not exist
     * @throws InsufficientStockException   if quantity exceeds current stock
     */
    InventoryItem reserve(String productId, int quantity);
}
