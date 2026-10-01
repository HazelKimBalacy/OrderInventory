package edu.cit.balacy.events;

/** Published by Inventory whenever a successful reserve or restock changes stock. */
public record InventoryStockChangedEvent(String productId, int available) {
}
