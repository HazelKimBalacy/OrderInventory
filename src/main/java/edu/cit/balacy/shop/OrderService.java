package edu.cit.balacy.shop;

import edu.cit.balacy.events.OrderCancelledEvent;
import edu.cit.balacy.events.OrderPlacedEvent;
import edu.cit.balacy.events.OrderRejectedEvent;
import edu.cit.balacy.inventory.InsufficientStockException;
import edu.cit.balacy.inventory.InventoryService;
import edu.cit.balacy.inventory.ProductNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order module. The constructor only ever mentions InventoryService (the
 * interface) - it has never seen InventoryServiceImpl and, because that class
 * is package-private in a different package, it physically could not import it
 * even if it wanted to.
 *
 * <p>It also never mentions the Notification module. Order announces what
 * happened via ApplicationEventPublisher and is done; whoever listens is not
 * Order's concern.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher events
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    /**
     * All-or-nothing multi-item order.
     *
     * <p>Every line is validated against current stock BEFORE a single unit is
     * reserved, so a failure on the last line cannot leave the earlier lines
     * partially fulfilled. Two details make that airtight:
     *
     * <ul>
     *   <li>Duplicate productIds in the cart are summed first, so asking for
     *       3 + 4 of a product with 5 in stock is caught as a request for 7,
     *       not waved through as two individually-valid lines.</li>
     *   <li>checkAvailability() takes the same pessimistic row lock reserve()
     *       does, and this whole method is one transaction, so the rows stay
     *       locked from the first check to the last write.</li>
     * </ul>
     *
     * <p>The surrounding @Transactional is the backstop: if anything threw
     * mid-reservation anyway, every decrement in this method rolls back
     * together as one unit of work.
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {

        Map<String, Integer> requested = aggregateByProduct(request.items());

        // --- Phase 1: validate everything, reserve nothing ------------------
        String rejectionReason = null;
        for (Map.Entry<String, Integer> line : requested.entrySet()) {
            try {
                inventoryService.checkAvailability(line.getKey(), line.getValue());
            } catch (ProductNotFoundException | InsufficientStockException ex) {
                rejectionReason = ex.getMessage();
                break;
            }
        }

        if (rejectionReason != null) {
            return rejectOrder(requested, rejectionReason);
        }

        // --- Phase 2: every line passed, so reserve them all ----------------
        for (Map.Entry<String, Integer> line : requested.entrySet()) {
            inventoryService.reserve(line.getKey(), line.getValue());
        }

        Order order = new Order(OrderStatus.CONFIRMED, null);
        requested.forEach(order::addItem);
        orderRepository.save(order);

        events.publishEvent(new OrderPlacedEvent(order.getOrderId(), toEventLines(requested)));

        return new OrderResponse(
                order.getOrderId(),
                OrderStatus.CONFIRMED,
                null,
                outcomes(requested, "RESERVED"),
                inventoryService.getItems(requested.keySet())
        );
    }

    private OrderResponse rejectOrder(Map<String, Integer> requested, String reason) {
        Order order = new Order(OrderStatus.REJECTED, reason);
        requested.forEach(order::addItem);
        orderRepository.save(order);

        events.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));

        return new OrderResponse(
                order.getOrderId(),
                OrderStatus.REJECTED,
                reason,
                outcomes(requested, "NOT_RESERVED"),
                inventoryService.getItems(requested.keySet())
        );
    }

    /**
     * Cancels a CONFIRMED order and returns every reserved line item to stock.
     *
     * @throws OrderNotFoundException       404 - no such order
     * @throws OrderNotCancellableException 409 - already CANCELLED (or REJECTED)
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }

        Map<String, Integer> restored = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
            restored.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        order.markCancelled();
        orderRepository.save(order);

        events.publishEvent(new OrderCancelledEvent(orderId, toEventLines(restored)));

        return new OrderResponse(
                orderId,
                OrderStatus.CANCELLED,
                null,
                outcomes(restored, "RESTOCKED"),
                inventoryService.getItems(restored.keySet())
        );
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderDTO::from)
                .toList();
    }

    /** Sums duplicate productIds so the validation sees the true total demand. */
    private Map<String, Integer> aggregateByProduct(List<OrderRequest.LineItem> items) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (OrderRequest.LineItem item : items) {
            totals.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return totals;
    }

    private List<OrderResponse.ItemOutcome> outcomes(Map<String, Integer> lines, String outcome) {
        List<OrderResponse.ItemOutcome> result = new ArrayList<>();
        lines.forEach((productId, qty) -> result.add(
                new OrderResponse.ItemOutcome(productId, qty, outcome)));
        return result;
    }

    private List<OrderPlacedEvent.Line> toEventLines(Map<String, Integer> lines) {
        List<OrderPlacedEvent.Line> result = new ArrayList<>();
        lines.forEach((productId, qty) -> result.add(new OrderPlacedEvent.Line(productId, qty)));
        return result;
    }
}
