package notifications.microservice.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for storing user notification preferences and settings.
 * Allows users to control how and when they receive notifications.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private Boolean smsEnabled = false;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "notification_frequency")
    private String notificationFrequency; // IMMEDIATE, DAILY, WEEKLY, MONTHLY

    @Column(name = "quiet_hours_start")
    private String quietHoursStart; // HH:mm format

    @Column(name = "quiet_hours_end")
    private String quietHoursEnd; // HH:mm format

    @Column(name = "opt_in_marketing", nullable = false)
    private Boolean optInMarketing = true;

    @Column(name = "opt_in_updates", nullable = false)
    private Boolean optInUpdates = true;

    @Column(name = "opt_in_promotions", nullable = false)
    private Boolean optInPromotions = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
