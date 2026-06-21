package notifications.microservice.service;

import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.repository.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {
    @Mock
    private NotificationEventRepository eventRepository;

    private NotificationEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new NotificationEvent();
        testEvent.setId(1L);
        testEvent.setMemberId("member123");
        testEvent.setTitle("Test Notification");
        testEvent.setDescription("Test Description");
        testEvent.setEventType("registration");
        testEvent.setStatus("CREATED");
        testEvent.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testSaveNotificationEvent() {
        when(eventRepository.save(any(NotificationEvent.class))).thenReturn(testEvent);

        NotificationEvent saved = eventRepository.save(testEvent);

        assertNotNull(saved);
        assertEquals("member123", saved.getMemberId());
        assertEquals("registration", saved.getEventType());
        verify(eventRepository, times(1)).save(testEvent);
    }

    @Test
    void testFindByMemberId() {
        List<NotificationEvent> events = Arrays.asList(testEvent);
        when(eventRepository.findByMemberId("member123")).thenReturn(events);

        List<NotificationEvent> result = eventRepository.findByMemberId("member123");

        assertEquals(1, result.size());
        assertEquals("member123", result.get(0).getMemberId());
    }

    @Test
    void testFindFailedEvents() {
        NotificationEvent failedEvent = new NotificationEvent();
        failedEvent.setStatus("FAILED");
        failedEvent.setErrorMessage("Connection timeout");

        List<NotificationEvent> events = Arrays.asList(failedEvent);
        when(eventRepository.findFailedAndRetryingEvents()).thenReturn(events);

        List<NotificationEvent> result = eventRepository.findFailedAndRetryingEvents();

        assertEquals(1, result.size());
        assertEquals("FAILED", result.get(0).getStatus());
    }
}

