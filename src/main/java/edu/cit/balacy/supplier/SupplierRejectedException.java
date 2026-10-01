package edu.cit.balacy.supplier;

/** Permanent problem (4xx other than auth/429). Retrying will not help. */
class SupplierRejectedException extends RuntimeException {
    SupplierRejectedException(String message) {
        super(message);
    }
}
