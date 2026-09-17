package edu.cit.balacy.inventory;

import java.util.Collection;
import java.util.List;

/**
 * Public contract for the Inventory module. This is the ONLY inventory type
 * (besides its exceptions and the read-only InventoryItem/DTO it returns)
 * that other modules, such as edu.cit.balacy.shop, are allowed to reference.
 * The implementation is intentionally package-private so nothing outside
 * this package can new it up, cast to it, or reach around the interface.
 */
public interface InventoryService {

    /**
     * @throws ProductNotFoundException if productId does not exist
     */
    InventoryItem getItem(String productId);

    /** Read model for GET /api/inventory - every product with its current stock. */
    List<InventoryItemDTO> getAllItems();

    /**
     * Read model for just the given products, in the order supplied. Used by
     * the Order module to report the post-order state of the products an
     * order actually touched, without it having to know the low-stock rule.
     * Unknown productIds are skipped rather than throwing.
     */
    List<InventoryItemDTO> getItems(Collection<String> productIds);

    /**
     * Checks that {@code quantity} units of {@code productId} are currently
     * available WITHOUT decrementing anything, taking the same pessimistic
     * row lock that {@link #reserve} takes.
     *
     * <p>The lock is what makes the lab's "validate everything before
     * reserving anything" rule safe: because the caller runs all of its
     * validations and all of its reservations inside one transaction, the
     * rows stay locked from the first check through the last write, so no
     * other request can slip in between and invalidate a check that already
     * passed.
     *
     * @throws ProductNotFoundException   if productId does not exist
     * @throws InsufficientStockException if quantity exceeds current stock
     */
    void checkAvailability(String productId, int quantity);

    /**
     * Atomically checks and decrements stock for productId by quantity.
     * Publishes a LowStockEvent if the remaining stock lands below the
     * configured threshold.
     *
     * @return the InventoryItem reflecting the post-reservation stock level
     * @throws ProductNotFoundException     if productId does not exist
     * @throws InsufficientStockException   if quantity exceeds current stock
     */
    InventoryItem reserve(String productId, int quantity);

    /**
     * Returns quantity units of productId to stock. Used by order
     * cancellation to undo a previous reserve().
     *
     * @return the InventoryItem reflecting the post-restock stock level
     * @throws ProductNotFoundException if productId does not exist
     */
    InventoryItem restock(String productId, int quantity);
}
