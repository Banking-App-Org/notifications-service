package notifications.microservice.service;

import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.repository.NotificationPreferenceRepository;
import notifications.microservice.service.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {
    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private NotificationPreferenceService preferenceService;
    private NotificationPreference testPreference;

    @BeforeEach
    void setUp() {
        preferenceService = new NotificationPreferenceService(preferenceRepository);

        testPreference = new NotificationPreference();
        testPreference.setMemberId("member123");
        testPreference.setEmailEnabled(true);
        testPreference.setPushEnabled(true);
        testPreference.setSmsEnabled(false);
        testPreference.setOptInMarketing(true);
        testPreference.setOptInUpdates(true);
        testPreference.setOptInPromotions(false);
    }

    @Test
    void testGetOrCreatePreferences_WhenExists() {
        when(preferenceRepository.findByMemberId("member123")).thenReturn(Optional.of(testPreference));

        NotificationPreference result = preferenceService.getOrCreatePreferences("member123");

        assertNotNull(result);
        assertEquals("member123", result.getMemberId());
        assertTrue(result.getEmailEnabled());
    }

    @Test
    void testGetOrCreatePreferences_WhenNotExists() {
        when(preferenceRepository.findByMemberId("newmember")).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(testPreference);

        NotificationPreference result = preferenceService.getOrCreatePreferences("newmember");

        assertNotNull(result);
        assertTrue(result.getEmailEnabled());
    }

    @Test
    void testShouldSendNotification_WithMarketingType() {
        when(preferenceRepository.findByMemberId("member123")).thenReturn(Optional.of(testPreference));

        boolean shouldSend = preferenceService.shouldSendNotification("member123", "marketing");

        assertTrue(shouldSend); // opt_in_marketing is true
    }

    @Test
    void testShouldSendNotification_WithPromotionType() {
        when(preferenceRepository.findByMemberId("member123")).thenReturn(Optional.of(testPreference));

        boolean shouldSend = preferenceService.shouldSendNotification("member123", "promotion");

        assertFalse(shouldSend); // opt_in_promotions is false
    }

    @Test
    void testUpdatePreferences() {
        NotificationPreference updates = new NotificationPreference();
        updates.setEmailEnabled(false);
        updates.setSmsEnabled(true);

        when(preferenceRepository.findByMemberId("member123")).thenReturn(Optional.of(testPreference));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(testPreference);

        NotificationPreference result = preferenceService.updatePreferences("member123", updates);

        assertNotNull(result);
    }
}

