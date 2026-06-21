package notifications.microservice;

import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.repository.NotificationEventRepository;
import notifications.microservice.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the notifications service.
 * Tests the complete flow including Kafka, database, and REST APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:29092", "port=29092" })
@TestPropertySource(properties = { "spring.kafka.bootstrap-servers=localhost:29092" })
class NotificationsServiceApplicationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationEventRepository eventRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        preferenceRepository.deleteAll();
    }

    @Test
    void testCreateNotificationPreferences() throws Exception {
        String memberId = "member123";

        mockMvc.perform(get("/api/notifications/preferences/" + memberId)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        Optional<NotificationPreference> pref = preferenceRepository.findByMemberId(memberId);
        assertTrue(pref.isPresent());
    }

    @Test
    void testUpdateNotificationPreferences() throws Exception {
        String memberId = "member456";

        String updateJson = "{\"emailEnabled\": false, \"smsEnabled\": true}";

        mockMvc.perform(put("/api/notifications/preferences/" + memberId)
                .contentType("application/json")
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.smsEnabled").value(true));
    }

    @Test
    void testSetQuietHours() throws Exception {
        String memberId = "member789";

        mockMvc.perform(patch("/api/notifications/preferences/" + memberId + "/quiet-hours")
                .param("start", "20:00")
                .param("end", "08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHoursStart").value("20:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("08:00"));
    }

    @Test
    void testDisableEmailNotifications() throws Exception {
        String memberId = "member999";

        mockMvc.perform(patch("/api/notifications/preferences/" + memberId + "/email/false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));
    }

    @Test
    void testGetNotificationEvents() throws Exception {
        String memberId = "member-events";

        // Create test events
        NotificationEvent event1 = new NotificationEvent();
        event1.setMemberId(memberId);
        event1.setTitle("Test Event 1");
        event1.setDescription("Description 1");
        event1.setEventType("registration");
        event1.setStatus("DELIVERED");
        eventRepository.save(event1);

        NotificationEvent event2 = new NotificationEvent();
        event2.setMemberId(memberId);
        event2.setTitle("Test Event 2");
        event2.setDescription("Description 2");
        event2.setEventType("payment");
        event2.setStatus("DELIVERED");
        eventRepository.save(event2);

        mockMvc.perform(get("/api/notifications/events/member/" + memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(memberId))
                .andExpect(jsonPath("$[0].eventType").value("registration"));

        List<NotificationEvent> events = eventRepository.findByMemberId(memberId);
        assertEquals(2, events.size());
    }

    @Test
    void testGetFailedNotifications() throws Exception {
        // Create failed event
        NotificationEvent failedEvent = new NotificationEvent();
        failedEvent.setMemberId("failed-member");
        failedEvent.setTitle("Failed Event");
        failedEvent.setEventType("enrollment");
        failedEvent.setStatus("FAILED");
        failedEvent.setErrorMessage("Connection timeout");
        failedEvent.setRetryCount(3);
        eventRepository.save(failedEvent);

        mockMvc.perform(get("/api/notifications/events/failed"))
                .andExpect(status().isOk());

        List<NotificationEvent> failedEvents = eventRepository.findFailedAndRetryingEvents();
        assertEquals(1, failedEvents.size());
        assertEquals("FAILED", failedEvents.get(0).getStatus());
    }

    @Test
    void testGetNotificationStats() throws Exception {
        // Create various events
        for (int i = 0; i < 10; i++) {
            NotificationEvent event = new NotificationEvent();
            event.setMemberId("member-" + i);
            event.setTitle("Event " + i);
            event.setEventType("registration");
            event.setStatus(i < 8 ? "DELIVERED" : "FAILED");
            eventRepository.save(event);
        }

        mockMvc.perform(get("/api/notifications/events/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(10))
                .andExpect(jsonPath("$.deliveredCount").value(8))
                .andExpect(jsonPath("$.failedCount").value(2));
    }

    @Test
    void testActuatorEndpoints() throws Exception {
        // Health check
        mockMvc.perform(get("/notifications-service/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Metrics list
        mockMvc.perform(get("/notifications-service/actuator/metrics"))
                .andExpect(status().isOk());

        // Prometheus metrics
        mockMvc.perform(get("/notifications-service/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory")));
    }

    @Test
    void testNotificationEventPersistence() {
        String memberId = "persistence-test";

        // Create and save event
        NotificationEvent event = new NotificationEvent();
        event.setMemberId(memberId);
        event.setTitle("Persistence Test");
        event.setDescription("Testing database persistence");
        event.setEventType("registration");
        event.setStatus("CREATED");
        event.setMetadata("{\"customData\": \"value\"}");

        NotificationEvent saved = eventRepository.save(event);
        assertNotNull(saved.getId());

        // Retrieve and verify
        Optional<NotificationEvent> retrieved = eventRepository.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Persistence Test", retrieved.get().getTitle());
        assertEquals("registration", retrieved.get().getEventType());
    }

    @Test
    void testPreferencePersistence() {
        String memberId = "pref-persistence-test";

        // Create and save preferences
        NotificationPreference pref = new NotificationPreference();
        pref.setMemberId(memberId);
        pref.setEmailEnabled(true);
        pref.setSmsEnabled(false);
        pref.setPushEnabled(true);
        pref.setQuietHoursStart("22:00");
        pref.setQuietHoursEnd("07:00");

        NotificationPreference saved = preferenceRepository.save(pref);
        assertNotNull(saved.getId());

        // Retrieve and verify
        Optional<NotificationPreference> retrieved = preferenceRepository.findByMemberId(memberId);
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().getEmailEnabled());
        assertEquals("22:00", retrieved.get().getQuietHoursStart());
    }
}

