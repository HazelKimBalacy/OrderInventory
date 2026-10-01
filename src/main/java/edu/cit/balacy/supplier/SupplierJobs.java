package edu.cit.balacy.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SupplierJobs {

    private static final Logger log = LoggerFactory.getLogger(SupplierJobs.class);

    private final SupplierGatewayImpl gateway;
    private final ProductCatalog catalog;

    SupplierJobs(SupplierGatewayImpl gateway, ProductCatalog catalog) {
        this.gateway = gateway;
        this.catalog = catalog;
    }

    /** Reads the supplier catalog shortly after startup, then hourly. */
    @Scheduled(fixedDelayString = "${supplier.catalog-refresh-ms:3600000}", initialDelay = 3000)
    void refreshCatalog() {
        try {
            catalog.refreshFromSupplier();
        } catch (RuntimeException e) {
            log.warn("Catalog refresh failed, using configured pack sizes: {}", e.getMessage());
        }
    }

    /** Sends reorders that could not be delivered earlier (never lose a reorder). */
    @Scheduled(fixedDelayString = "${supplier.submit-interval-ms:10000}", initialDelay = 8000)
    void resubmitPending() {
        gateway.submitPending();
    }

    /** Polls open POs and publishes SupplierOrderDeliveredEvent on delivery. */
    @Scheduled(fixedDelayString = "${supplier.poll-interval-ms:30000}", initialDelay = 15000)
    void trackOrders() {
        gateway.trackOpenOrders();
    }
}
