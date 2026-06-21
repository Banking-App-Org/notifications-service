package notifications.microservice.service;

import notifications.microservice.dto.BankingEvent;
import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.repository.NotificationEventRepository;
import notifications.microservice.repository.NotificationPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class LoadTestService {

    private static final int USER_POOL_SIZE = 1000;
    private static final int CHUNK_SIZE = 500;
    private static final String[] EVENT_TYPES = {
        "USER_REGISTERED", "DEPOSIT_COMPLETED", "WITHDRAWAL_COMPLETED",
        "TRANSFER_COMPLETED", "PAYMENT_PROCESSED"
    };

    private final NotificationEventRepository eventRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationsService notificationsService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LoadTestStatus currentStatus;

    @Autowired
    public LoadTestService(NotificationEventRepository eventRepository,
                           NotificationPreferenceRepository preferenceRepository,
                           @Lazy NotificationsService notificationsService) {
        this.eventRepository = eventRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationsService = notificationsService;
    }

    public LoadTestStatus start(int count, int concurrency) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A load test is already running");
        }

        LoadTestStatus status = new LoadTestStatus(count, concurrency);
        currentStatus = status;

        Thread thread = new Thread(() -> {
            try {
                run(count, concurrency, status);
            } catch (Exception e) {
                log.error("Load test failed unexpectedly", e);
                status.markFailed(e.getMessage());
            } finally {
                running.set(false);
            }
        }, "load-test-main");
        thread.setDaemon(true);
        thread.start();

        return status;
    }

    public LoadTestStatus getStatus() {
        return currentStatus;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int cleanupTestData() {
        return eventRepository.deleteLoadTestEventsByMemberId();
    }

    private void run(int count, int concurrency, LoadTestStatus status) throws InterruptedException {
        log.info("Load test starting: count={}, concurrency={}", count, concurrency);

        log.info("Pre-warming {} synthetic users...", USER_POOL_SIZE);
        warmUserPool();
        log.info("User pool ready — starting measurement");

        status.markStarted();

        int taskCount = (count + CHUNK_SIZE - 1) / CHUNK_SIZE;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(taskCount);

        try {
            for (int t = 0; t < taskCount; t++) {
                final int chunkStart = t * CHUNK_SIZE;
                final int chunkEnd = Math.min(chunkStart + CHUNK_SIZE, count);
                pool.submit(() -> {
                    try {
                        processChunk(chunkStart, chunkEnd, status);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } finally {
            pool.shutdown();
        }

        status.markCompleted();
        long elapsedMs = status.getElapsedMs();
        log.info("============================================================");
        log.info("  LOAD TEST COMPLETE");
        log.info("  Notifications : {}/{} succeeded ({} failed)",
                status.getCompleted(), status.getTotal(), status.getFailed());
        log.info("  Threads       : {}", concurrency);
        log.info("  Duration      : {}.{}s",  elapsedMs / 1000, String.format("%03d", elapsedMs % 1000));
        log.info("  Throughput    : {}/s", String.format("%.1f", status.getThroughputPerSecond()));
        log.info("============================================================");
    }

    private void warmUserPool() {
        for (int i = 0; i < USER_POOL_SIZE; i++) {
            String userId = syntheticUserId(i);
            NotificationPreference pref = preferenceRepository.findByMemberId(userId)
                    .orElse(new NotificationPreference());
            pref.setMemberId(userId);
            pref.setEmail(userId + "@mailhog.local");
            pref.setEmailEnabled(false); // disabled so generation doesn't send email; ResendService sends via MailHog instead
            pref.setSmsEnabled(false);
            pref.setPushEnabled(true);
            pref.setNotificationFrequency("IMMEDIATE");
            pref.setOptInMarketing(true);
            pref.setOptInUpdates(true);
            pref.setOptInPromotions(false);
            preferenceRepository.save(pref);
        }
    }

    private void processChunk(int from, int to, LoadTestStatus status) {
        for (int i = from; i < to; i++) {
            try {
                String userId = syntheticUserId(i % USER_POOL_SIZE);
                String eventType = EVENT_TYPES[i % EVENT_TYPES.length];

                BankingEvent event = new BankingEvent();
                event.setUserId(userId);
                event.setEventType(eventType);
                event.setAmount(100.0);
                event.setCurrency("USD");

                notificationsService.sendBankingNotification(
                        event,
                        "Load Test: " + eventType,
                        "Synthetic notification #" + i,
                        eventType
                );
                status.addCompleted(1);
            } catch (Exception e) {
                log.warn("Notification failed at index {}: {}", i, e.getMessage());
                status.addFailed(1);
            }
        }
    }

    private static String syntheticUserId(int n) {
        return "lt-user-" + String.format("%04d", n);
    }

    // -------------------------------------------------------------------------

    public static class LoadTestStatus {

        private final int total;
        private final int concurrency;
        private final AtomicLong completed = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
        private volatile String state = "PENDING";
        private volatile long startMs = 0;
        private volatile long endMs = 0;
        private volatile String errorMessage;

        LoadTestStatus(int total, int concurrency) {
            this.total = total;
            this.concurrency = concurrency;
        }

        void markStarted() {
            startMs = System.currentTimeMillis();
            state = "RUNNING";
        }

        void markCompleted() {
            endMs = System.currentTimeMillis();
            state = "COMPLETED";
        }

        void markFailed(String error) {
            endMs = System.currentTimeMillis();
            state = "FAILED";
            errorMessage = error;
        }

        void addCompleted(long n) { completed.addAndGet(n); }
        void addFailed(long n)    { failed.addAndGet(n); }

        public int    getTotal()       { return total; }
        public int    getConcurrency() { return concurrency; }
        public long   getCompleted()   { return completed.get(); }
        public long   getFailed()      { return failed.get(); }
        public String getState()       { return state; }
        public String getErrorMessage(){ return errorMessage; }

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
