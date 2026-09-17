package edu.cit.balacy.notification;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    /**
     * ORDER_CONFIRMED / ORDER_REJECTED / ORDER_CANCELLED / LOW_STOCK.
     * Beyond the minimum columns the spec asks for, but the lab requires
     * low-stock entries to be "distinct" from order entries, and a type
     * column does that honestly instead of making the UI string-match
     * the message text.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
        // required by JPA
    }

    Notification(NotificationType type, String message) {
        this.type = type;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
