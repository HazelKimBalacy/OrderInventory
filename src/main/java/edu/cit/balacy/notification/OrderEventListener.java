package edu.cit.balacy.notification;

import edu.cit.balacy.events.LowStockEvent;
import edu.cit.balacy.events.OrderCancelledEvent;
import edu.cit.balacy.events.OrderPlacedEvent;
import edu.cit.balacy.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * The Notification module's only entry point from the rest of the system.
 *
 * <p>Note what this class imports: four event records from
 * edu.cit.balacy.events, and nothing else. It never touches OrderService or
 * InventoryService, it cannot ask them anything, and it cannot stop them -
 * everything it needs has to be carried on the event itself. The dependency
 * is one-way by construction: shop and inventory don't import this package
 * at all, so they can be compiled, tested, and reasoned about with the
 * Notification module deleted entirely.
 */
@Component
class OrderEventListener {

    private final NotificationService notifications;

    OrderEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    void on(OrderPlacedEvent event) {
        notifications.record(
                NotificationType.ORDER_CONFIRMED,
                "Order O" + event.orderId() + " confirmed (" + summarize(event) + ")");
    }

    @EventListener
    void on(OrderRejectedEvent event) {
        notifications.record(
                NotificationType.ORDER_REJECTED,
                "Order O" + event.orderId() + " rejected: " + event.reason());
    }

    @EventListener
    void on(OrderCancelledEvent event) {
        String items = event.restoredItems().stream()
                .map(l -> l.quantity() + "x " + l.productId())
                .collect(Collectors.joining(", "));
        notifications.record(
                NotificationType.ORDER_CANCELLED,
                "Order O" + event.orderId() + " cancelled, restocked " + items);
    }

    @EventListener
    void on(LowStockEvent event) {
        notifications.record(
                NotificationType.LOW_STOCK,
                "Reorder needed: " + event.productId() + " (" + event.name() + ") down to "
                        + event.remainingStock() + ", below threshold of " + event.threshold());
    }

    private String summarize(OrderPlacedEvent event) {
        return event.items().stream()
                .map(l -> l.quantity() + "x " + l.productId())
                .collect(Collectors.joining(", "));
    }
}
