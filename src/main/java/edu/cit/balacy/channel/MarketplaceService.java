package edu.cit.balacy.channel;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import edu.cit.balacy.inventory.InventoryItem;
import edu.cit.balacy.inventory.InventoryService;
import edu.cit.balacy.inventory.ProductNotFoundException;
import edu.cit.balacy.shop.OrderRequest;
import edu.cit.balacy.shop.OrderResponse;
import edu.cit.balacy.shop.OrderService;
import edu.cit.balacy.shop.OrderStatus;
import edu.cit.balacy.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class MarketplaceService implements MarketplaceChannel {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);
    private static final int CURSOR_ID = 1;

    private final MarketplaceCursorRepository cursorRepository;
    private final ProcessedFeedEventRepository eventRepository;
    private final MarketplaceOrderRecordRepository orderRepository;
    private final StockSyncRecordRepository stockRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ObjectMapper objectMapper;

    MarketplaceService(
            MarketplaceCursorRepository cursorRepository,
            ProcessedFeedEventRepository eventRepository,
            MarketplaceOrderRecordRepository orderRepository,
            StockSyncRecordRepository stockRepository,
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            ObjectMapper objectMapper) {
        this.cursorRepository = cursorRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    long currentCursor() {
        return cursorRepository.findById(CURSOR_ID).map(MarketplaceCursor::getFeedCursor).orElse(0L);
    }

    @Transactional
    void processFeedEvent(MarketplaceApiClient.FeedEvent event) {
        if (eventRepository.existsById(event.eventId())) {
            advanceCursorInTransaction(event.seq());
            return;
        }

        switch (event.type()) {
            case "ORDER_PLACED" -> recordPlacedOrder(event);
            case "ORDER_CANCELLED" -> recordCancellation(event);
            default -> log.warn("Skipping unsupported Tiangge feed event type '{}' (event {})",
                    event.type(), event.eventId());
        }
        eventRepository.save(new ProcessedFeedEvent(event.eventId(), event.seq()));
        advanceCursorInTransaction(event.seq());
    }

    @Transactional
    void advanceCursor(long nextCursor) {
        advanceCursorInTransaction(nextCursor);
    }

    private void advanceCursorInTransaction(long nextCursor) {
        MarketplaceCursor cursor = cursorRepository.findById(CURSOR_ID)
                .orElseGet(() -> new MarketplaceCursor(nextCursor));
        if (nextCursor > cursor.getFeedCursor()) {
            cursor.setFeedCursor(nextCursor);
        }
        cursorRepository.save(cursor);
    }

    private void recordPlacedOrder(MarketplaceApiClient.FeedEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("Tiangge ORDER_PLACED event has no orderId");
        }
        if (orderRepository.existsById(event.orderId())) {
            return;
        }
        if (event.lines() == null || event.lines().isEmpty()) {
            throw new IllegalArgumentException("Tiangge order " + event.orderId() + " has no lines");
        }

        Map<String, Integer> requested = new LinkedHashMap<>();
        for (MarketplaceApiClient.FeedLine line : event.lines()) {
            if (line.sellerSku() == null || line.sellerSku().isBlank() || line.qty() < 1) {
                throw new IllegalArgumentException("Tiangge order " + event.orderId() + " has an invalid line");
            }
            requested.merge(line.sellerSku(), line.qty(), Math::addExact);
        }
        List<MarketplaceLine> lines = requested.entrySet().stream()
                .map(line -> new MarketplaceLine(line.getKey(), line.getValue()))
                .toList();
        OrderResponse response = orderService.placeOrder(toOrderRequest(lines));
        if (response.orderId() == null) {
            throw new IllegalStateException("OrderService returned no local ID for Tiangge order " + event.orderId());
        }
        String decision = response.status() == OrderStatus.CONFIRMED
                ? MarketplaceDecision.ACCEPTED.name() : null;
        MarketplaceOrderRecord record = new MarketplaceOrderRecord(
                event.orderId(), response.orderId(), encodeLines(lines), decision);
        record.setReason(response.reason());
        orderRepository.save(record);
    }

    private void recordCancellation(MarketplaceApiClient.FeedEvent event) {
        MarketplaceOrderRecord record = orderRepository.findById(event.orderId()).orElse(null);
        if (record == null) {
            log.warn("Tiangge cancellation {} arrived without a recorded marketplace order", event.orderId());
            return;
        }
        if (MarketplaceDecision.ACCEPTED.name().equals(record.getDecision())
                && !record.isCancellationPending() && !record.isCancellationSent()) {
            orderService.cancelOrderIdempotently(record.getLocalOrderId());
            record.setCancellationPending(true);
            orderRepository.save(record);
        } else if (MarketplaceDecision.BACKORDERED.name().equals(record.getDecision())
                && record.getResolution() == null) {
            record.setResolution("CANCELLED");
            orderRepository.save(record);
        }
    }

    void evaluateUndecidedOrders() {
        for (MarketplaceOrderRecord record : orderRepository.findByDecisionIsNullOrderByTianggeOrderIdAsc()) {
            evaluateOrder(record);
        }
    }

    private void evaluateOrder(MarketplaceOrderRecord record) {
        List<MarketplaceLine> lines = decodeLines(record.getLinesJson());
        Map<String, Integer> stockByProduct = stockByProduct(lines);
        boolean canFill = allLinesAvailable(lines, stockByProduct);

        if (canFill) {
            OrderResponse retried = orderService.retryRejectedOrder(record.getLocalOrderId());
            if (retried.status() == OrderStatus.CONFIRMED) {
                record.setDecision(MarketplaceDecision.ACCEPTED.name());
                record.setReason(null);
                orderRepository.save(record);
                return;
            }
            record.setReason(retried.reason());
            stockByProduct = stockByProduct(lines);
        }

        boolean hasShortage = false;
        boolean everyShortageHasSupplierOrder = true;
        for (MarketplaceLine line : lines) {
            int available = stockByProduct.getOrDefault(line.productId(), -1);
            if (available >= line.quantity()) {
                continue;
            }
            hasShortage = true;
            if (available < 0 || !supplierGateway.supportsProduct(line.productId())) {
                everyShortageHasSupplierOrder = false;
                continue;
            }
            if (!supplierGateway.isReorderOnTheWay(line.productId())) {
                supplierGateway.requestReorder(line.productId(), line.quantity() - available);
            }
            if (!supplierGateway.isReorderOnTheWay(line.productId())) {
                everyShortageHasSupplierOrder = false;
            }
        }

        record.setDecision(hasShortage && everyShortageHasSupplierOrder
                ? MarketplaceDecision.BACKORDERED.name()
                : MarketplaceDecision.REJECTED.name());
        if (record.getReason() == null || record.getReason().isBlank()) {
            record.setReason("Insufficient inventory");
        }
        orderRepository.save(record);
    }

    @Transactional
    void resolveBackorders() {
        for (MarketplaceOrderRecord record : orderRepository.findByDecisionAndResolutionIsNullOrderByTianggeOrderIdAsc(
                MarketplaceDecision.BACKORDERED.name())) {
            resolveBackorder(record);
        }
    }

    private void resolveBackorder(MarketplaceOrderRecord record) {
        List<MarketplaceLine> lines = decodeLines(record.getLinesJson());
        Map<String, Integer> stockByProduct = stockByProduct(lines);
        boolean canFill = allLinesAvailable(lines, stockByProduct);
        if (canFill) {
            OrderResponse response = orderService.retryRejectedOrder(record.getLocalOrderId());
            if (response.status() == OrderStatus.CONFIRMED) {
                record.setResolution("ACCEPTED");
                orderRepository.save(record);
                return;
            }
            stockByProduct = stockByProduct(lines);
        }

        for (MarketplaceLine line : lines) {
            int available = stockByProduct.getOrDefault(line.productId(), -1);
            if (available < line.quantity()
                    && available >= 0
                    && supplierGateway.isReorderOnTheWay(line.productId())) {
                return;
            }
        }
        record.setResolution("CANCELLED");
        orderRepository.save(record);
    }

    @Transactional(readOnly = true)
    List<MarketplaceOrderRecord> pendingDecisions() {
        return orderRepository.findByDecisionIsNotNullAndDecisionSentFalseOrderByTianggeOrderIdAsc();
    }

    @Transactional(readOnly = true)
    List<MarketplaceOrderRecord> pendingCancellations() {
        return orderRepository.findByCancellationPendingTrueAndCancellationSentFalseOrderByTianggeOrderIdAsc();
    }

    @Transactional(readOnly = true)
    List<MarketplaceOrderRecord> pendingResolutions() {
        return orderRepository.findByResolutionIsNotNullAndResolutionSentFalseOrderByTianggeOrderIdAsc();
    }

    @Transactional(readOnly = true)
    List<StockSyncRecord> pendingStock() {
        return stockRepository.findAll();
    }

    @Transactional
    void markDecisionSent(String orderId) {
        orderRepository.findById(orderId).ifPresent(record -> {
            record.setDecisionSent(true);
            orderRepository.save(record);
        });
    }

    @Transactional
    void markCancellationSent(String orderId) {
        orderRepository.findById(orderId).ifPresent(record -> {
            record.setCancellationSent(true);
            orderRepository.save(record);
        });
    }

    @Transactional
    void markResolutionSent(String orderId) {
        orderRepository.findById(orderId).ifPresent(record -> {
            record.setResolutionSent(true);
            orderRepository.save(record);
        });
    }

    @Transactional
    void markStockSent(String productId, int sentAvailable) {
        stockRepository.findById(productId)
                .filter(record -> record.getAvailable() == sentAvailable)
                .ifPresent(stockRepository::delete);
    }

    @Override
    @Transactional
    public void publishStockChanged(String productId, int available) {
        int currentAvailable = inventoryService.getItem(productId).getStock();
        StockSyncRecord record = stockRepository.findById(productId)
                .orElseGet(() -> new StockSyncRecord(productId, currentAvailable));
        record.setAvailable(currentAvailable);
        stockRepository.save(record);
    }

    private Map<String, Integer> stockByProduct(List<MarketplaceLine> lines) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (MarketplaceLine line : lines) {
            if (!stock.containsKey(line.productId())) {
                try {
                    InventoryItem item = inventoryService.getItem(line.productId());
                    stock.put(line.productId(), item.getStock());
                } catch (ProductNotFoundException e) {
                    stock.put(line.productId(), -1);
                }
            }
        }
        return stock;
    }

    private boolean allLinesAvailable(List<MarketplaceLine> lines, Map<String, Integer> stockByProduct) {
        for (MarketplaceLine line : lines) {
            if (stockByProduct.getOrDefault(line.productId(), -1) < line.quantity()) {
                return false;
            }
        }
        return true;
    }

    private OrderRequest toOrderRequest(List<MarketplaceLine> lines) {
        return new OrderRequest(lines.stream()
                .map(line -> new OrderRequest.LineItem(line.productId(), line.quantity()))
                .toList());
    }

    private String encodeLines(List<MarketplaceLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not persist Tiangge order lines", e);
        }
    }

    private List<MarketplaceLine> decodeLines(String linesJson) {
        try {
            return objectMapper.readValue(linesJson, new TypeReference<>() { });
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not read persisted Tiangge order lines", e);
        }
    }
}
