package edu.cit.balacy.shop;

/** Thrown when a cancel targets an orderId that doesn't exist -> HTTP 404. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("No such order: " + orderId);
    }
}
