package edu.cit.balacy.inventory;

/**
 * Thrown when a reservation asks for more units than are currently in
 * stock. Public (unlike InventoryServiceImpl) because the Order module
 * needs to catch it across the module boundary.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock for " + productId + ": requested " + requested
                + " but only " + available + " available");
    }
}
