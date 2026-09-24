package edu.cit.balacy.supplier;

import edu.cit.balacy.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    /** A reorder in any of these states already "covers" the product. */
    private static final List<SupplierOrderStatus> OPEN = List.of(
            SupplierOrderStatus.PENDING, SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.IN_TRANSIT);

    /** States that have a PO number and are worth polling. */
    private static final List<SupplierOrderStatus> TRACKED = List.of(
            SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.IN_TRANSIT);

    private static final int MAX_CASES_PER_ORDER = 99;

    private final SupplierOrderRepository repo;
    private final LegacySupplyClient client;
    private final ProductCatalog catalog;
    private final XmlTranslator translator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final int maxStatusChecksPerRun;

    SupplierGatewayImpl(SupplierOrderRepository repo,
                        LegacySupplyClient client,
                        ProductCatalog catalog,
                        XmlTranslator translator,
                        ApplicationEventPublisher events,
                        PlatformTransactionManager txManager,
                        @Value("${supplier.max-status-checks-per-run:10}") int maxStatusChecksPerRun) {
        this.repo = repo;
        this.client = client;
        this.catalog = catalog;
        this.translator = translator;
        this.events = events;
        this.maxStatusChecksPerRun = maxStatusChecksPerRun;
        this.tx = new TransactionTemplate(txManager);
        // Always a fresh transaction, even when called from an AFTER_COMMIT phase.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ API

    @Override
    public SupplierOrderResult requestReorder(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("unitsNeeded must be positive");
        }
        ProductCatalog.Entry entry = catalog.lookup(productId);

        // Don't stack reorders while one is already on its way.
        var existing = repo.findFirstByProductIdAndStatusIn(productId, OPEN);
        if (existing.isPresent()) {
            log.info("Reorder for {} skipped: open order {} already exists", productId, existing.get().getBuyerRef());
            return toResult(existing.get());
        }

        int cases = (unitsNeeded + entry.packSize() - 1) / entry.packSize();   // round UP
        if (cases > MAX_CASES_PER_ORDER) {   // manual: Qty is a whole number from 1 to 99
            log.warn("Reorder for {} capped at {} cases (wanted {})", productId, MAX_CASES_PER_ORDER, cases);
            cases = MAX_CASES_PER_ORDER;
        }
        final int orderCases = cases;
        final int units = cases * entry.packSize();

        // 1) Persist first. The request_id is fixed from this moment on.
        Long id = tx.execute(s -> {
            SupplierOrder o = repo.saveAndFlush(new SupplierOrder(productId, UUID.randomUUID().toString(), orderCases, units));
            o.setBuyerRef("RO-" + o.getId());
            repo.save(o);
            return o.getId();
        });

        // 2) Then try to send it. Outside any transaction. If the supplier is down the
        //    order simply stays PENDING and SupplierJobs sends it later.
        try {
            submit(id);
        } catch (SupplierUnavailableException e) {
            // already logged in submit(); nothing is lost
        }

        return toResult(repo.findById(id).orElseThrow());
    }

    // ------------------------------------------------------- package-private
    // (called by SupplierJobs)

    /** Sends one PENDING order. On transient failure it simply stays PENDING. */
    void submit(Long id) {
        SupplierOrder o = repo.findById(id).orElseThrow();
        if (o.getStatus() != SupplierOrderStatus.PENDING) {
            return;
        }
        try {
            LegacySupplyClient.Ack ack = client.createPurchaseOrder(
                    o.getRequestId(), o.getBuyerRef(), catalog.lookup(o.getProductId()).sku(), o.getCases());

            recordAck(id, o.getBuyerRef(), ack);

        } catch (SupplierUnavailableException e) {
            log.warn("{} stays PENDING, supplier unavailable: {}", o.getBuyerRef(), e.getMessage());
            throw e;
        } catch (SupplierRejectedException e) {
            log.error("{} FAILED permanently: {}", o.getBuyerRef(), e.getMessage());
            update(id, ord -> ord.setStatus(SupplierOrderStatus.FAILED));
        }
    }

    /**
     * Retry path. An earlier attempt may have reached LegacySupply even though we never
     * saw the answer, so look the order up by BuyerRef first and adopt it if it exists.
     * Otherwise send it again with the SAME X-Request-Id.
     */
    void resubmit(Long id) {
        SupplierOrder o = repo.findById(id).orElseThrow();
        if (o.getStatus() != SupplierOrderStatus.PENDING) {
            return;
        }
        try {
            var found = client.findByBuyerRef(o.getBuyerRef());
            if (found.isPresent()) {
                log.info("{} already exists at the supplier, adopting it", o.getBuyerRef());
                recordAck(id, o.getBuyerRef(), found.get());
                return;
            }
        } catch (SupplierRejectedException e) {
            log.warn("BuyerRef lookup for {} rejected ({}), sending normally", o.getBuyerRef(), e.getMessage());
        }
        submit(id);
    }

    private void recordAck(Long id, String buyerRef, LegacySupplyClient.Ack ack) {
        SupplierOrderStatus mapped = translator.mapStatus(ack.statusCode());
        SupplierOrderStatus status = (mapped == SupplierOrderStatus.UNKNOWN || mapped == SupplierOrderStatus.PENDING)
                ? SupplierOrderStatus.SUBMITTED : mapped;
        update(id, ord -> {
            ord.setPoNumber(ack.poNumber());
            ord.setStatus(status);
        });
        log.info("{} recorded as PO {} ({})", buyerRef, ack.poNumber(), status);
    }

    /** Retries every PENDING order, oldest first. Stops at the first outage. */
    void submitPending() {
        for (SupplierOrder o : repo.findByStatusOrderByIdAsc(SupplierOrderStatus.PENDING)) {
            try {
                resubmit(o.getId());
            } catch (SupplierUnavailableException e) {
                return; // supplier is down: don't hammer it for every remaining order
            }
        }
    }

    /** Polls a limited number of open POs (quota-friendly) and applies status changes. */
    void trackOpenOrders() {
        var batch = repo.findByStatusInOrderByUpdatedAtAsc(TRACKED, PageRequest.of(0, maxStatusChecksPerRun));
        for (SupplierOrder o : batch) {
            if (o.getPoNumber() == null) {
                continue;
            }
            try {
                String code = client.fetchStatusCode(o.getPoNumber());
                applyStatus(o.getId(), code);
            } catch (SupplierUnavailableException e) {
                log.warn("Status polling paused: {}", e.getMessage());
                return;
            } catch (SupplierRejectedException e) {
                log.error("Status check for {} rejected: {}", o.getBuyerRef(), e.getMessage());
                update(o.getId(), SupplierOrder::touch);
            }
        }
    }

    // --------------------------------------------------------------- private

    private void applyStatus(Long id, String supplierCode) {
        SupplierOrderStatus next = translator.mapStatus(supplierCode);
        tx.executeWithoutResult(s -> {
            SupplierOrder o = repo.findById(id).orElseThrow();

            if (next == SupplierOrderStatus.UNKNOWN) {
                // POLICY: unexpected status -> keep the order open, keep its current
                // status, log it, never restock. A human can look at the log.
                log.warn("Unexpected supplier status '{}' for {}; keeping status {}",
                        supplierCode, o.getBuyerRef(), o.getStatus());
                o.touch();
                repo.save(o);
                return;
            }
            if (o.getStatus() == SupplierOrderStatus.DELIVERED) {
                return; // terminal: never restock twice
            }

            boolean justDelivered = next == SupplierOrderStatus.DELIVERED;
            o.setStatus(next);
            o.touch();
            repo.save(o);

            if (justDelivered) {
                // Same transaction as the status change: restock and DELIVERED commit together.
                events.publishEvent(new SupplierOrderDeliveredEvent(o.getProductId(), o.getUnits()));
                log.info("{} delivered, restocking {} units of {}", o.getBuyerRef(), o.getUnits(), o.getProductId());
            }
        });
    }

    private void update(Long id, Consumer<SupplierOrder> change) {
        tx.executeWithoutResult(s -> {
            SupplierOrder o = repo.findById(id).orElseThrow();
            change.accept(o);
            repo.save(o);
        });
    }

    private SupplierOrderResult toResult(SupplierOrder o) {
        return new SupplierOrderResult(o.getId(), o.getBuyerRef(), o.getProductId(), o.getUnits(), o.getStatus());
    }
}
