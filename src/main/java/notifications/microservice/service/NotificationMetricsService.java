package notifications.microservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for tracking custom metrics and analytics.
 * Exposes metrics for Prometheus monitoring and alerting.
 */
@Slf4j
@Service
public class NotificationMetricsService {
    private final MeterRegistry meterRegistry;
    private final Counter registrationCounter;
    private final Counter enrollmentCounter;
    private final Counter cancellationCounter;
    private final Counter paymentCounter;
    private final Counter failedNotifications;
    private final Timer processingTimer;

    @Autowired
    public NotificationMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters for each notification type
        this.registrationCounter = Counter.builder("notifications.registration.total")
                .description("Total registration notifications sent")
                .register(meterRegistry);

        this.enrollmentCounter = Counter.builder("notifications.enrollment.total")
                .description("Total enrollment notifications sent")
                .register(meterRegistry);

        this.cancellationCounter = Counter.builder("notifications.cancellation.total")
                .description("Total cancellation notifications sent")
                .register(meterRegistry);

        this.paymentCounter = Counter.builder("notifications.payment.total")
                .description("Total payment notifications sent")
                .register(meterRegistry);

        this.failedNotifications = Counter.builder("notifications.failed.total")
                .description("Total failed notifications")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("notifications.processing.time")
                .description("Time taken to process notifications")
                .register(meterRegistry);
    }

    public void recordRegistration() {
        registrationCounter.increment();
    }

    public void recordEnrollment() {
        enrollmentCounter.increment();
    }

    public void recordCancellation() {
        cancellationCounter.increment();
    }

    public void recordPayment() {
        paymentCounter.increment();
    }

    public void recordFailedNotification() {
        failedNotifications.increment();
    }

    public Timer.Sample recordProcessingStart() {
        return Timer.start(meterRegistry);
    }

    public void recordProcessingTime(Timer.Sample sample) {
        sample.stop(processingTimer);
    }

    public void recordNotificationBatch(String eventType, int count) {
        meterRegistry.counter("notifications.batch.processed",
                "event_type", eventType).increment(count);
    }
}

