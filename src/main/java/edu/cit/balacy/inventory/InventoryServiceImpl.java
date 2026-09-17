package edu.cit.balacy.inventory;

import edu.cit.balacy.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher events;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher events,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold
    ) {
        this.inventoryRepository = inventoryRepository;
        this.events = events;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ProductNotFoundException.class)
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getAllItems() {
        return inventoryRepository.findAllByOrderByProductIdAsc().stream()
                .map(item -> InventoryItemDTO.from(item, lowStockThreshold))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getItems(Collection<String> productIds) {
        return productIds.stream()
                .map(id -> inventoryRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(item -> InventoryItemDTO.from(item, lowStockThreshold))
                .toList();
    }

    @Override
    @Transactional(noRollbackFor = {ProductNotFoundException.class, InsufficientStockException.class})
    public void checkAvailability(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (quantity > item.getStock()) {
            throw new InsufficientStockException(productId, quantity, item.getStock());
        }
    }

    @Override
    @Transactional(noRollbackFor = {ProductNotFoundException.class, InsufficientStockException.class})
    public InventoryItem reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (quantity > item.getStock()) {
            throw new InsufficientStockException(productId, quantity, item.getStock());
        }

        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);

        // Business rule: any successful reservation that leaves stock below
        // the threshold raises a reorder signal. Published as an event rather
        // than calling Notification directly - Inventory doesn't know that a
        // Notification module exists.
        if (saved.getStock() < lowStockThreshold) {
            events.publishEvent(new LowStockEvent(
                    saved.getProductId(), saved.getName(), saved.getStock(), lowStockThreshold));
        }

        return saved;
    }

    @Override
    @Transactional(noRollbackFor = ProductNotFoundException.class)
    public InventoryItem restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        item.setStock(item.getStock() + quantity);
        return inventoryRepository.save(item);
    }
}
