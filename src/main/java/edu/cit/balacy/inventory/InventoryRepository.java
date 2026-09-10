package edu.cit.balacy.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Locks the row for the duration of the transaction so two concurrent
     * reservations on the same product can't both read the same stock
     * value and both succeed (a classic lost-update race).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findByProductId(String productId);
}
