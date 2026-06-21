package notifications.microservice.service;

import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.repository.NotificationPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for managing user notification preferences.
 * Determines whether a user should receive a notification based on their settings.
 */
@Slf4j
@Service
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository preferenceRepository;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Get or create default notification preferences for a member
     */
    public NotificationPreference getOrCreatePreferences(String memberId) {
        Optional<NotificationPreference> existing = preferenceRepository.findByMemberId(memberId);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create default preferences for new members
        NotificationPreference prefs = new NotificationPreference();
        prefs.setMemberId(memberId);
        prefs.setEmailEnabled(true);
        prefs.setPushEnabled(true);
        prefs.setSmsEnabled(false);
        prefs.setNotificationFrequency("IMMEDIATE");
        prefs.setOptInMarketing(true);
        prefs.setOptInUpdates(true);
        prefs.setOptInPromotions(false);

        return preferenceRepository.save(prefs);
    }

    /**
     * Check if a notification should be sent based on user preferences
     */
    public boolean shouldSendNotification(String memberId, String notificationType) {
        try {
            NotificationPreference prefs = getOrCreatePreferences(memberId);

            // Check if user is in quiet hours
            if (isInQuietHours(prefs)) {
                log.info("Notification suppressed for member {} due to quiet hours", memberId);
                return false;
            }

            // Check notification type preferences
            switch (notificationType.toLowerCase()) {
                case "marketing":
                    return prefs.getOptInMarketing();
                case "promotion":
                    return prefs.getOptInPromotions();
                case "update":
                    return prefs.getOptInUpdates();
                default:
                    return true; // Always send critical notifications
            }
        } catch (Exception e) {
            log.error("Error checking notification preferences for member {}", memberId, e);
            return true; // Default to sending if preference check fails
        }
    }

    /**
     * Check if current time is within quiet hours
     */
    private boolean isInQuietHours(NotificationPreference prefs) {
        if (prefs.getQuietHoursStart() == null || prefs.getQuietHoursEnd() == null) {
            return false;
        }

        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(prefs.getQuietHoursStart(), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(prefs.getQuietHoursEnd(), TIME_FORMATTER);

            if (start.isBefore(end)) {
                return now.isAfter(start) && now.isBefore(end);
            } else {
                // Handle case where quiet hours span midnight
                return now.isAfter(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            log.warn("Error parsing quiet hours for member", e);
            return false;
        }
    }

    /**
     * Get or create preferences seeded with contact details from a registration event.
     * Enables SMS automatically when a phone number is provided.
     */
    public NotificationPreference getOrCreatePreferencesWithContact(String userId, String email, String phone) {
        Optional<NotificationPreference> existing = preferenceRepository.findByMemberId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        NotificationPreference prefs = new NotificationPreference();
        prefs.setMemberId(userId);
        prefs.setEmailEnabled(true);
        prefs.setPushEnabled(true);
        prefs.setNotificationFrequency("IMMEDIATE");
        prefs.setOptInMarketing(true);
        prefs.setOptInUpdates(true);
        prefs.setOptInPromotions(false);

        if (email != null && !email.isBlank()) {
            prefs.setEmail(email.trim());
        }
        if (phone != null && !phone.isBlank()) {
            String cleanPhone = phone.trim().replaceAll("[^0-9+]", "");
            prefs.setPhoneNumber(cleanPhone.isEmpty() ? null : cleanPhone);
            prefs.setSmsEnabled(!cleanPhone.isEmpty());
        } else {
            prefs.setSmsEnabled(false);
        }

        return preferenceRepository.save(prefs);
    }

    /**
     * Syncs email and phone from a USER_UPDATED event into existing preferences.
     * Only overwrites if the incoming value is non-blank, so a partial update
     * (e.g. only email changed) doesn't wipe the stored phone number.
     * Also enables SMS if a phone number is now present and wasn't before.
     */
    public NotificationPreference syncContactDetails(String userId, String email, String phone) {
        NotificationPreference prefs = getOrCreatePreferences(userId);

        if (email != null && !email.isBlank()) {
            prefs.setEmail(email.trim());
        }
        if (phone != null && !phone.isBlank()) {
            String cleanPhone = phone.trim().replaceAll("[^0-9+]", "");
            if (!cleanPhone.isEmpty()) {
                prefs.setPhoneNumber(cleanPhone);
                if (!Boolean.TRUE.equals(prefs.getSmsEnabled())) {
                    prefs.setSmsEnabled(true);
                }
            }
        }

        return preferenceRepository.save(prefs);
    }

    /**
     * Update notification preferences for a member
     */
    public NotificationPreference updatePreferences(String memberId, NotificationPreference updates) {
        NotificationPreference prefs = getOrCreatePreferences(memberId);

        if (updates.getEmailEnabled() != null) {
            prefs.setEmailEnabled(updates.getEmailEnabled());
        }
        if (updates.getEmail() != null) {
            prefs.setEmail(updates.getEmail().trim());
        }
        if (updates.getSmsEnabled() != null) {
            prefs.setSmsEnabled(updates.getSmsEnabled());
        }
        if (updates.getPushEnabled() != null) {
            prefs.setPushEnabled(updates.getPushEnabled());
        }
        if (updates.getNotificationFrequency() != null) {
            prefs.setNotificationFrequency(updates.getNotificationFrequency());
        }
        if (updates.getQuietHoursStart() != null) {
            prefs.setQuietHoursStart(updates.getQuietHoursStart());
        }
        if (updates.getQuietHoursEnd() != null) {
            prefs.setQuietHoursEnd(updates.getQuietHoursEnd());
        }
        if (updates.getOptInMarketing() != null) {
            prefs.setOptInMarketing(updates.getOptInMarketing());
        }
        if (updates.getOptInUpdates() != null) {
            prefs.setOptInUpdates(updates.getOptInUpdates());
        }
        if (updates.getOptInPromotions() != null) {
            prefs.setOptInPromotions(updates.getOptInPromotions());
        }
        if (updates.getPhoneNumber() != null) {
            String phone = updates.getPhoneNumber().trim();
            // Infobip prefers plain digits, but we allow some symbols and clean them up here
            String cleanPhone = phone.replaceAll("[^0-9+]", "");
            prefs.setPhoneNumber(cleanPhone.isEmpty() ? null : cleanPhone);
        }

        return preferenceRepository.save(prefs);
    }
}
