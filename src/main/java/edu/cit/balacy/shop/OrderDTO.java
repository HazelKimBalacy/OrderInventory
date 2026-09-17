package edu.cit.balacy.shop;

import java.time.Instant;
import java.util.List;

/** Read model for GET /api/orders. */
public record OrderDTO(
        Long orderId,
        OrderStatus status,
        String reason,
        Instant createdAt,
        List<Line> items
) {
    public record Line(String productId, int quantity) {
    }

    static OrderDTO from(Order order) {
        return new OrderDTO(
                order.getOrderId(),
                order.getStatus(),
                order.getReason(),
                order.getCreatedAt(),
                order.getItems().stream()
                        .map(i -> new Line(i.getProductId(), i.getQuantity()))
                        .toList()
        );
    }
}
