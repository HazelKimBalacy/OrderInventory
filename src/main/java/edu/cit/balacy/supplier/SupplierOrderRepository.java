package edu.cit.balacy.supplier;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    Optional<SupplierOrder> findFirstByProductIdAndStatusIn(String productId, Collection<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByStatusOrderByIdAsc(SupplierOrderStatus status);

    List<SupplierOrder> findByStatusInOrderByUpdatedAtAsc(Collection<SupplierOrderStatus> statuses, Pageable pageable);
}
