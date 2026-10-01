package edu.cit.balacy.supplier;

/** Our own status vocabulary. LegacySupply codes are translated into this. */
public enum SupplierOrderStatus {
    PENDING,      // stored locally, not yet accepted by the supplier
    SUBMITTED,    // supplier accepted it, we hold a PO number
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,    // supplier cancelled/rejected after accepting
    FAILED,       // supplier refused the request permanently (bad SKU, etc.)
    UNKNOWN       // only used as a translation result; never stored
}
