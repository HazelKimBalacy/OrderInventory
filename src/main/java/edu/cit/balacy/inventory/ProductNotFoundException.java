package edu.cit.balacy.inventory;

/** Thrown when a productId does not exist in the inventory table. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("No such product: " + productId);
    }
}
