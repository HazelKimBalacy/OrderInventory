package edu.cit.balacy.channel;

import edu.cit.balacy.inventory.InventoryItemDTO;
import edu.cit.balacy.inventory.InventoryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
class MarketplaceLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceLifecycle.class);

    private final MarketplaceApiClient client;
    private final ChannelListingCatalog catalog;
    private final InventoryService inventoryService;
    private final MarketplaceOutboxDispatcher outbox;
    private final Instant startedAt = Instant.now();
    private volatile boolean initialized;

    MarketplaceLifecycle(
            MarketplaceApiClient client,
            ChannelListingCatalog catalog,
            InventoryService inventoryService,
            MarketplaceOutboxDispatcher outbox) {
        this.client = client;
        this.catalog = catalog;
        this.inventoryService = inventoryService;
        this.outbox = outbox;
    }

    boolean isInitialized() {
        return initialized;
    }

    @PostConstruct
    void onStarting() {
        if (!client.isConfigured()) {
            log.error("Tiangge integration is disabled: set LS_CLIENT_ID and LS_API_KEY");
            return;
        }
        heartbeatAndInitialize();
    }

    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    void heartbeat() {
        if (!client.isConfigured()) {
            return;
        }
        heartbeatAndInitialize();
    }

    private void heartbeatAndInitialize() {
        try {
            client.heartbeat("Activity02", startedAt, Instant.now().getEpochSecond() - startedAt.getEpochSecond());
            if (!initialized) {
                initializeShop();
            }
        } catch (RuntimeException e) {
            log.error("Tiangge heartbeat or initial shop publication failed; it will be retried", e);
        }
    }

    private void initializeShop() {
        Map<String, String> supplierSkus = catalog.supplierSkus();
        List<InventoryItemDTO> inventory = inventoryService.getAllItems();
        List<Map<String, String>> listings = new ArrayList<>();
        List<Map<String, Object>> stock = new ArrayList<>();
        for (InventoryItemDTO item : inventory) {
            String supplierSku = supplierSkus.get(item.productId());
            if (supplierSku == null) {
                continue;
            }
            listings.add(Map.of(
                    "sellerSku", item.productId(),
                    "title", item.name(),
                    "supplierSku", supplierSku));
            stock.add(Map.of("sellerSku", item.productId(), "available", item.stock()));
        }
        if (listings.isEmpty()) {
            throw new IllegalStateException("No Tiangge listings configured; check SUPPLIER_CATALOG");
        }
        if (listings.size() < 3) {
            log.error("Only {} Tiangge listings are configured; at least 3 are required", listings.size());
        }
        client.publishListings(listings);
        client.publishStock(stock);
        initialized = true;
        log.info("Tiangge shop initialized with {} listings; instance ID is active", listings.size());
        outbox.dispatchPending();
    }
}
