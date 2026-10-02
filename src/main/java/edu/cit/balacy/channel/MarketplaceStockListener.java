package edu.cit.balacy.channel;

import edu.cit.balacy.events.InventoryStockChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class MarketplaceStockListener {

    private final MarketplaceChannel channel;

    MarketplaceStockListener(MarketplaceChannel channel) {
        this.channel = channel;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    void on(InventoryStockChangedEvent event) {
        channel.publishStockChanged(event.productId(), event.available());
    }
}
