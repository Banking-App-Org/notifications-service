package notifications.microservice.service;

import lombok.extern.slf4j.Slf4j;
import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.repository.NotificationEventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class NotificationSeederService {

    private static final int BATCH_SIZE = 1000;
    private static final String[] EVENT_TYPES = {
        "USER_REGISTERED", "DEPOSIT_COMPLETED", "WITHDRAWAL_COMPLETED",
        "TRANSFER_COMPLETED", "PAYMENT_PROCESSED"
    };

    private final NotificationEventRepository eventRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SeederStatus currentStatus;

    public NotificationSeederService(NotificationEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public SeederStatus start(int count) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A seed job is already running");
        }
        SeederStatus status = new SeederStatus(count);
        currentStatus = status;
        Thread thread = new Thread(() -> {
            try {
                run(count, status);
            } catch (Exception e) {
                log.error("Seeder failed", e);
                status.markFailed(e.getMessage());
            } finally {
                running.set(false);
            }
        }, "seeder-main");
        thread.setDaemon(true);
        thread.start();
        return status;
    }

    public SeederStatus getStatus() { return currentStatus; }
    public boolean isRunning() { return running.get(); }

    public int cleanup() {
        return eventRepository.deleteLoadTestEventsByMemberId();
    }

    private void run(int count, SeederStatus status) {
        log.info("Seeder starting: count={}", count);
        status.markStarted();

        List<NotificationEvent> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < count; i++) {
            String eventType = EVENT_TYPES[i % EVENT_TYPES.length];
            NotificationEvent event = new NotificationEvent();
            event.setMemberId("lt-user-" + String.format("%04d", i % 1000));
            event.setTitle(eventType.replace('_', ' '));
            event.setDescription("Seeded notification #" + i);
            event.setEventType(eventType);
            if (i % 5 == 4) {
                event.setStatus("FAILED");
                event.setErrorMessage("Simulated delivery failure");
            }
            batch.add(event);

            if (batch.size() == BATCH_SIZE || i == count - 1) {
                try {
                    eventRepository.saveAll(batch);
                    status.addCompleted(batch.size());
                } catch (Exception e) {
                    status.addFailed(batch.size());
                    log.warn("Batch insert failed at i={}: {}", i, e.getMessage());
                }
                batch.clear();
            }
        }

        status.markCompleted();
        long elapsedMs = status.getElapsedMs();
        log.info("============================================================");
        log.info("  SEEDER COMPLETE");
        log.info("  Inserted  : {}/{} ({} failed)", status.getCompleted(), count, status.getFailed());
        log.info("  Duration  : {}.{}s", elapsedMs / 1000, String.format("%03d", elapsedMs % 1000));
        log.info("  Throughput: {}/s", String.format("%.1f", status.getThroughputPerSecond()));
        log.info("============================================================");
    }

    // -------------------------------------------------------------------------

    public static class SeederStatus {

        private final int total;
        private final AtomicLong completed = new AtomicLong(0);
        private final AtomicLong failed    = new AtomicLong(0);
        private volatile String state = "PENDING";
        private volatile long startMs = 0;
        private volatile long endMs   = 0;
        private volatile String errorMessage;

        SeederStatus(int total) { this.total = total; }

        void markStarted()           { startMs = System.currentTimeMillis(); state = "RUNNING"; }
        void markCompleted()         { endMs = System.currentTimeMillis(); state = "COMPLETED"; }
        void markFailed(String err)  { endMs = System.currentTimeMillis(); state = "FAILED"; errorMessage = err; }

        void addCompleted(long n) { completed.addAndGet(n); }
        void addFailed(long n)    { failed.addAndGet(n); }

        public int    getTotal()        { return total; }
        public long   getCompleted()    { return completed.get(); }
        public long   getFailed()       { return failed.get(); }
        public String getState()        { return state; }
        public String getErrorMessage() { return errorMessage; }

        public double getProgressPercent() {
            return total == 0 ? 0 : (completed.get() + failed.get()) * 100.0 / total;
        }

        public double getThroughputPerSecond() {
            long elapsed = elapsedMs();
            return elapsed <= 0 ? 0 : completed.get() * 1000.0 / elapsed;
        }

        public long getElapsedMs() { return elapsedMs(); }

        private long elapsedMs() {
            if (startMs == 0) return 0;
            return (endMs > 0 ? endMs : System.currentTimeMillis()) - startMs;
        }
    }
}
