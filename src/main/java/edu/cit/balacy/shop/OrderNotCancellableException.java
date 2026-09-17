package edu.cit.balacy.shop;

/**
 * Thrown when a cancel targets an order that isn't in a cancellable state
 * -> HTTP 409. Covers the spec's "already CANCELLED" case, and also REJECTED
 * orders: those never reserved any stock, so there is nothing to restock and
 * cancelling one would be meaningless rather than merely redundant.
 */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(Long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be cancelled because its status is " + status);
    }
}
