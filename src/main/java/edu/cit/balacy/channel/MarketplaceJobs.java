package edu.cit.balacy.channel;

import edu.cit.balacy.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class MarketplaceJobs {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceJobs.class);
    private static final int FEED_LIMIT = 50;

    private final MarketplaceLifecycle lifecycle;
    private final MarketplaceApiClient client;
    private final MarketplaceService service;
    private final MarketplaceOutboxDispatcher outbox;

    MarketplaceJobs(
            MarketplaceLifecycle lifecycle,
            MarketplaceApiClient client,
            MarketplaceService service,
            MarketplaceOutboxDispatcher outbox) {
        this.lifecycle = lifecycle;
        this.client = client;
        this.service = service;
        this.outbox = outbox;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 3000)
    void pollFeed() {
        if (!lifecycle.isInitialized()) {
            return;
        }
        try {
            outbox.dispatchPending();
            service.evaluateUndecidedOrders();
            MarketplaceApiClient.FeedPage page = client.fetchFeed(service.currentCursor(), FEED_LIMIT);
            if (page == null || page.events() == null) {
                throw new IllegalStateException("Tiangge returned an invalid feed response");
            }
            for (MarketplaceApiClient.FeedEvent event : page.events()) {
                service.processFeedEvent(event);
                service.evaluateUndecidedOrders();
                outbox.dispatchPending();
            }
            service.advanceCursor(page.nextCursor());
            outbox.dispatchPending();
        } catch (RuntimeException e) {
            log.error("Tiangge feed processing failed; the durable cursor will be retried", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSupplierDelivery(SupplierOrderDeliveredEvent event) {
        if (!lifecycle.isInitialized()) {
            return;
        }
        try {
            service.resolveBackorders();
            outbox.dispatchPending();
        } catch (RuntimeException e) {
            log.error("Marketplace backorders could not be resolved after delivery of {}",
                    event.productId(), e);
        }
    }
}
