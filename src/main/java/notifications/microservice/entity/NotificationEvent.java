package notifications.microservice.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing an immutable notification event for audit trail and event sourcing.
 * This enables compliance auditing, event replay, and debugging capabilities.
 */
@Entity
@Table(name = "notification_events", indexes = {
        @Index(name = "idx_member_id", columnList = "member_id"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_event_type", columnList = "event_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_type", nullable = false)
    private String eventType; // registration, enrollment, cancellation, payment, etc.

    @Column(name = "status", nullable = false)
    private String status; // CREATED, DELIVERED, FAILED, RETRYING

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON payload for additional context

    @Column(name = "resent", nullable = false)
    private boolean resent = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "CREATED";
        }
    }
}

