package edu.cit.balacy.inventory;

import edu.cit.balacy.events.SupplierOrderDeliveredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Inventory's only entry point from the supplier flow. It imports the event
 * record and nothing from the supplier module.
 */
@Component
class RestockListener {

    private final InventoryService inventory;

    RestockListener(InventoryService inventory) {
        this.inventory = inventory;
    }

    @EventListener
    void on(SupplierOrderDeliveredEvent event) {
        inventory.restock(event.productId(), event.units());
    }
}
