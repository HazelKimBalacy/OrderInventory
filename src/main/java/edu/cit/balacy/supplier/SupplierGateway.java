package edu.cit.balacy.supplier;

/**
 * The ONLY entry point into the supplier module. Speaks our language:
 * product id + units needed. Cases, SKUs, XML and supplier status codes
 * never cross this line.
 */
public interface SupplierGateway {

    /**
     * Requests a reorder covering at least {@code unitsNeeded} units.
     * Never throws because the supplier is down: the reorder is stored as
     * PENDING and sent later by a scheduled job.
     * If an open reorder already exists for the product, that one is returned.
     */
    SupplierOrderResult requestReorder(String productId, int unitsNeeded);

    /** Whether a supplier purchase order for this product is accepted or in transit. */
    boolean isReorderOnTheWay(String productId);

    boolean supportsProduct(String productId);
}
