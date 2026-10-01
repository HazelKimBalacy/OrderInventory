package edu.cit.balacy.supplier;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", unique = true)
    private String buyerRef;

    /** Generated once at creation and reused on every retry and after restarts. */
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierOrder() {
    }

    SupplierOrder(String productId, String requestId, int cases, int units) {
        this.productId = productId;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Forces updated_at to change so status polling rotates fairly across orders. */
    void touch() {
        updatedAt = Instant.now();
    }

    Long getId() { return id; }
    String getProductId() { return productId; }
    String getBuyerRef() { return buyerRef; }
    void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    String getRequestId() { return requestId; }
    String getPoNumber() { return poNumber; }
    void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    int getCases() { return cases; }
    int getUnits() { return units; }
    SupplierOrderStatus getStatus() { return status; }
    void setStatus(SupplierOrderStatus status) { this.status = status; }
}
