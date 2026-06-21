package notifications.microservice.controller;

import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.repository.NotificationEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for accessing notification event audit trail.
 * Supports querying notification history and troubleshooting failures.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications/events")
public class NotificationEventController {
    private final NotificationEventRepository eventRepository;

    @Autowired
    public NotificationEventController(NotificationEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Get all notification events for a member
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<NotificationEvent>> getMemberEvents(@PathVariable String memberId) {
        List<NotificationEvent> events = eventRepository.findByMemberId(memberId);
        return ResponseEntity.ok(events);
    }

    /**
     * Get failed notifications for manual intervention
     */
    @GetMapping("/failed")
    public ResponseEntity<List<NotificationEvent>> getFailedNotifications() {
        List<NotificationEvent> failedEvents = eventRepository.findFailedAndRetryingEvents();
        log.info("Retrieved {} failed/retrying events", failedEvents.size());
        return ResponseEntity.ok(failedEvents);
    }

    /**
     * Get notifications by event type
     */
    @GetMapping("/type/{eventType}")
    public ResponseEntity<List<NotificationEvent>> getByEventType(@PathVariable String eventType) {
        List<NotificationEvent> events = eventRepository.findByStatus(eventType);
        return ResponseEntity.ok(events);
    }

    /**
     * Get a specific notification event
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<NotificationEvent> getEvent(@PathVariable Long eventId) {
        return eventRepository.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<NotificationStats> getStats() {
        List<NotificationEvent> allEvents = eventRepository.findAll();

        NotificationStats stats = new NotificationStats();
        stats.setTotalEvents(allEvents.size());
        stats.setDeliveredCount((int) allEvents.stream().filter(e -> "DELIVERED".equals(e.getStatus())).count());
        stats.setFailedCount((int) allEvents.stream().filter(e -> "FAILED".equals(e.getStatus())).count());
        stats.setRetryingCount((int) allEvents.stream().filter(e -> "RETRYING".equals(e.getStatus())).count());

        return ResponseEntity.ok(stats);
    }

    public static class NotificationStats {
        private Integer totalEvents;
        private Integer deliveredCount;
        private Integer failedCount;
        private Integer retryingCount;

        public NotificationStats() {}

        public Integer getTotalEvents() { return totalEvents; }
        public void setTotalEvents(Integer totalEvents) { this.totalEvents = totalEvents; }

        public Integer getDeliveredCount() { return deliveredCount; }
        public void setDeliveredCount(Integer deliveredCount) { this.deliveredCount = deliveredCount; }

        public Integer getFailedCount() { return failedCount; }
        public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }

        public Integer getRetryingCount() { return retryingCount; }
        public void setRetryingCount(Integer retryingCount) { this.retryingCount = retryingCount; }
    }
}

