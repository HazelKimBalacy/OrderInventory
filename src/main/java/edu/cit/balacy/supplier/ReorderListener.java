package edu.cit.balacy.supplier;

import edu.cit.balacy.events.LowStockEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The auto-reorder rule. Reacts to Inventory's LowStockEvent and calls the
 * gateway. Runs AFTER the customer's transaction commits and on its own
 * single thread, so a slow or dead supplier never delays or breaks an order,
 * and reorders are processed one at a time.
 */
@Component
class ReorderListener {

    private static final Logger log = LoggerFactory.getLogger(ReorderListener.class);

    private final SupplierGateway gateway;
    private final int targetStock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "supplier-reorder");
        t.setDaemon(true);
        return t;
    });

    ReorderListener(SupplierGateway gateway,
                    @Value("${supplier.reorder-target-stock:20}") int targetStock) {
        this.gateway = gateway;
        this.targetStock = targetStock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(LowStockEvent event) {
        int unitsNeeded = Math.max(1, targetStock - event.remainingStock());
        worker.submit(() -> {
            try {
                gateway.requestReorder(event.productId(), unitsNeeded);
            } catch (RuntimeException e) {
                log.error("Reorder for {} could not be created", event.productId(), e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
