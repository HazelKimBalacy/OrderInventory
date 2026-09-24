package edu.cit.balacy.events;

/**
 * Published by the supplier module when a reorder has been delivered.
 * Expressed in OUR terms: our product id and the number of units to add.
 * No cases, no pack sizes, no supplier SKUs.
 */
public record SupplierOrderDeliveredEvent(String productId, int units) {
}
