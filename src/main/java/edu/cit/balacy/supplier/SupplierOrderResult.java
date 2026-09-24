package edu.cit.balacy.supplier;

public record SupplierOrderResult(
        Long orderId,
        String buyerRef,
        String productId,
        int unitsOrdered,
        SupplierOrderStatus status
) {
}
