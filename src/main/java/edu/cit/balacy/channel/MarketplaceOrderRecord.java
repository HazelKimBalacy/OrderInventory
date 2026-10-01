package edu.cit.balacy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_orders")
class MarketplaceOrderRecord {

    @Id
    @Column(name = "tiangge_order_id", columnDefinition = "text")
    private String tianggeOrderId;

    @Column(name = "local_order_id", nullable = false)
    private Long localOrderId;

    @Column(name = "lines_json", nullable = false, columnDefinition = "text")
    private String linesJson;

    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "decision_sent", nullable = false)
    private boolean decisionSent;

    @Column(name = "cancellation_pending", nullable = false)
    private boolean cancellationPending;

    @Column(name = "cancellation_sent", nullable = false)
    private boolean cancellationSent;

    @Column(name = "resolution", length = 20)
    private String resolution;

    @Column(name = "resolution_sent", nullable = false)
    private boolean resolutionSent;

    protected MarketplaceOrderRecord() {
    }

    MarketplaceOrderRecord(String tianggeOrderId, Long localOrderId, String linesJson, String decision) {
        this.tianggeOrderId = tianggeOrderId;
        this.localOrderId = localOrderId;
        this.linesJson = linesJson;
        this.decision = decision;
    }

    String getTianggeOrderId() { return tianggeOrderId; }
    Long getLocalOrderId() { return localOrderId; }
    void setLocalOrderId(Long localOrderId) { this.localOrderId = localOrderId; }
    String getLinesJson() { return linesJson; }
    String getDecision() { return decision; }
    void setDecision(String decision) { this.decision = decision; }
    String getReason() { return reason; }
    void setReason(String reason) { this.reason = reason; }
    boolean isDecisionSent() { return decisionSent; }
    void setDecisionSent(boolean decisionSent) { this.decisionSent = decisionSent; }
    boolean isCancellationPending() { return cancellationPending; }
    void setCancellationPending(boolean cancellationPending) { this.cancellationPending = cancellationPending; }
    boolean isCancellationSent() { return cancellationSent; }
    void setCancellationSent(boolean cancellationSent) { this.cancellationSent = cancellationSent; }
    String getResolution() { return resolution; }
    void setResolution(String resolution) { this.resolution = resolution; }
    boolean isResolutionSent() { return resolutionSent; }
    void setResolutionSent(boolean resolutionSent) { this.resolutionSent = resolutionSent; }
}
