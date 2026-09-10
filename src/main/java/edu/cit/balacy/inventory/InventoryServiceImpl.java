package edu.cit.balacy.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ProductNotFoundException.class)
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
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
        return inventoryRepository.save(item);
    }
}