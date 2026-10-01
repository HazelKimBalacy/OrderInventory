package edu.cit.balacy.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
class MarketplaceOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceOutboxDispatcher.class);

    private final MarketplaceService service;
    private final MarketplaceApiClient client;
    private final ChannelListingCatalog catalog;

    MarketplaceOutboxDispatcher(
            MarketplaceService service,
            MarketplaceApiClient client,
            ChannelListingCatalog catalog) {
        this.service = service;
        this.client = client;
        this.catalog = catalog;
    }

    void dispatchPending() {
        if (!dispatchDecisions() || !dispatchCancellations() || !dispatchResolutions()) {
            return;
        }
        dispatchStock();
    }

    private boolean dispatchDecisions() {
        for (MarketplaceOrderRecord record : service.pendingDecisions()) {
            try {
                client.sendDecision(
                        record.getTianggeOrderId(),
                        MarketplaceDecision.valueOf(record.getDecision()),
                        record.getLocalOrderId(),
                        record.getReason());
                service.markDecisionSent(record.getTianggeOrderId());
            } catch (RuntimeException e) {
                log.error("Tiangge decision for order {} remains pending",
                        record.getTianggeOrderId(), e);
                return false;
            }
        }
        return true;
    }

    private boolean dispatchCancellations() {
        for (MarketplaceOrderRecord record : service.pendingCancellations()) {
            try {
                client.confirmCancellation(record.getTianggeOrderId());
                service.markCancellationSent(record.getTianggeOrderId());
            } catch (RuntimeException e) {
                log.error("Tiangge cancellation confirmation for order {} remains pending",
                        record.getTianggeOrderId(), e);
                return false;
            }
        }
        return true;
    }

    private boolean dispatchResolutions() {
        for (MarketplaceOrderRecord record : service.pendingResolutions()) {
            try {
                client.resolveBackorder(record.getTianggeOrderId(), record.getResolution());
                service.markResolutionSent(record.getTianggeOrderId());
            } catch (RuntimeException e) {
                log.error("Tiangge backorder resolution for order {} remains pending",
                        record.getTianggeOrderId(), e);
                return false;
            }
        }
        return true;
    }

    void dispatchStock() {
        List<Map<String, Object>> updates = new ArrayList<>();
        List<StockSyncRecord> queuedRecords = new ArrayList<>();
        Map<String, String> supplierSkus = catalog.supplierSkus();
        for (StockSyncRecord record : service.pendingStock()) {
            String supplierSku = supplierSkus.get(record.getProductId());
            if (supplierSku == null) {
                log.warn("Stock changed for unlisted product {}; no Tiangge stock update is needed",
                        record.getProductId());
                service.markStockSent(record.getProductId(), record.getAvailable());
                continue;
            }
            updates.add(Map.of("sellerSku", record.getProductId(), "available", record.getAvailable()));
            queuedRecords.add(record);
        }
        if (updates.isEmpty()) {
            return;
        }
        try {
            client.publishStock(updates);
            queuedRecords.forEach(record ->
                    service.markStockSent(record.getProductId(), record.getAvailable()));
        } catch (RuntimeException e) {
            log.error("Tiangge stock updates remain queued for products {}",
                    queuedRecords.stream().map(StockSyncRecord::getProductId).toList(), e);
        }
    }
}
