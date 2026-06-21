package notifications.microservice.service;

import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.repository.NotificationEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Service for handling messages that fail processing and are routed to the
 * Dead Letter Topic (DLT). Records failed notifications for audit and manual intervention.
 */
@Slf4j
@Service
public class DeadLetterQueueService {

    private final NotificationEventRepository notificationEventRepository;

    @Autowired
    public DeadLetterQueueService(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    @KafkaListener(topics = "notification-events-dlt", groupId = "notifications-service")
    public void handleNotificationDLT(@Payload String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Message received in DLQ for topic: {}, message: {}", topic, message);

        try {
            NotificationEvent event = new NotificationEvent();
            event.setStatus("FAILED");
            event.setEventType("NOTIFICATION_PROCESSING_FAILED");
            event.setErrorMessage("Message sent to DLQ - processing failed after retries");
            event.setRetryCount(3);
            event.setMetadata(message);

            notificationEventRepository.save(event);
            log.info("Failed notification event recorded for manual intervention: topic={}, message={}",
                    topic, message);
        } catch (Exception e) {
            log.error("Failed to record DLT message in database", e);
        }
    }
}