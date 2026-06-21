package notifications.microservice.controller;

import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.service.NotificationPreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for managing user notification preferences.
 * Allows users to control notification channels, frequency, and opt-in/opt-out settings.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;

    @Autowired
    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * Get notification preferences for a member
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<NotificationPreference> getPreferences(@PathVariable String memberId) {
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(memberId);
        return ResponseEntity.ok(prefs);
    }

    /**
     * Update notification preferences for a member
     */
    @PutMapping("/{memberId}")
    public ResponseEntity<NotificationPreference> updatePreferences(
            @PathVariable String memberId,
            @RequestBody NotificationPreference updates) {
        log.info("Updating preferences for member: {}", memberId);
        NotificationPreference updated = preferenceService.updatePreferences(memberId, updates);
        return ResponseEntity.ok(updated);
    }

    /**
     * Enable/disable email notifications
     */
    @PatchMapping("/{memberId}/email/{enabled}")
    public ResponseEntity<NotificationPreference> setEmailNotifications(
            @PathVariable String memberId,
            @PathVariable Boolean enabled) {
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(memberId);
        prefs.setEmailEnabled(enabled);

        NotificationPreference updated = preferenceService.updatePreferences(memberId, prefs);
        return ResponseEntity.ok(updated);
    }

    /**
     * Enable/disable SMS notifications
     */
    @PatchMapping("/{memberId}/sms/{enabled}")
    public ResponseEntity<NotificationPreference> setSmsNotifications(
            @PathVariable String memberId,
            @PathVariable Boolean enabled) {
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(memberId);
        prefs.setSmsEnabled(enabled);

        NotificationPreference updated = preferenceService.updatePreferences(memberId, prefs);
        return ResponseEntity.ok(updated);
    }

    /**
     * Enable/disable push notifications
     */
    @PatchMapping("/{memberId}/push/{enabled}")
    public ResponseEntity<NotificationPreference> setPushNotifications(
            @PathVariable String memberId,
            @PathVariable Boolean enabled) {
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(memberId);
        prefs.setPushEnabled(enabled);

        NotificationPreference updated = preferenceService.updatePreferences(memberId, prefs);
        return ResponseEntity.ok(updated);
    }

    /**
     * Set quiet hours (no notifications)
     */
    @PatchMapping("/{memberId}/quiet-hours")
    public ResponseEntity<NotificationPreference> setQuietHours(
            @PathVariable String memberId,
            @RequestParam String start,
            @RequestParam String end) {
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(memberId);
        prefs.setQuietHoursStart(start);
        prefs.setQuietHoursEnd(end);

        NotificationPreference updated = preferenceService.updatePreferences(memberId, prefs);
        log.info("Quiet hours set for member {} - {} to {}", memberId, start, end);
        return ResponseEntity.ok(updated);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPreferenceInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}

