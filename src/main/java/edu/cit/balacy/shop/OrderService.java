package edu.cit.balacy.shop;

import edu.cit.balacy.inventory.InsufficientStockException;
import edu.cit.balacy.inventory.InventoryItem;
import edu.cit.balacy.inventory.InventoryItemDTO;
import edu.cit.balacy.inventory.InventoryService;
import edu.cit.balacy.inventory.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order module. Notice the constructor only ever mentions InventoryService
 * (the interface) - it has never seen InventoryServiceImpl and, because that
 * class is package-private in a different package, it physically could not
 * import it even if it wanted to. This is plain in-process Java method
 * calls (no HTTP, no serialization, no network) but the dependency still
 * only flows through the published contract.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        try {
            InventoryItem updated = inventoryService.reserve(request.productId(), request.quantity());

            Order order = new Order(request.productId(), request.quantity(), OrderStatus.CONFIRMED, null);
            orderRepository.save(order);

            return new OrderResponse(OrderStatus.CONFIRMED, null, InventoryItemDTO.from(updated));

        } catch (ProductNotFoundException | InsufficientStockException ex) {
            String reason = ex.getMessage();

            Order order = new Order(request.productId(), request.quantity(), OrderStatus.REJECTED, reason);
            orderRepository.save(order);

            InventoryItemDTO currentState = currentInventoryStateOrNull(request.productId());
            return new OrderResponse(OrderStatus.REJECTED, reason, currentState);
        }
    }

    private InventoryItemDTO currentInventoryStateOrNull(String productId) {
        try {
            return InventoryItemDTO.from(inventoryService.getItem(productId));
        } catch (ProductNotFoundException ex) {
            return null;
        }
    }
}
