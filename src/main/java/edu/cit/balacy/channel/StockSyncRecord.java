package edu.cit.balacy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_stock_outbox")
class StockSyncRecord {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "available", nullable = false)
    private int available;

    protected StockSyncRecord() {
    }

    StockSyncRecord(String productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    String getProductId() { return productId; }
    int getAvailable() { return available; }
    void setAvailable(int available) { this.available = available; }
}
