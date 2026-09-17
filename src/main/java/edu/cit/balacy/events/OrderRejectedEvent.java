package edu.cit.balacy.events;

/** Published by the Order module when an order is REJECTED (nothing reserved). */
public record OrderRejectedEvent(
        Long orderId,
        String reason
) {
}
