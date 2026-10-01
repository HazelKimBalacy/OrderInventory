package edu.cit.balacy.supplier;

/** Transient problem (timeout, refused, 5xx, 429). Safe to retry later. */
class SupplierUnavailableException extends RuntimeException {
    SupplierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
