package notifications.microservice.service;

import lombok.extern.slf4j.Slf4j;
import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.repository.NotificationEventRepository;
import notifications.microservice.repository.NotificationPreferenceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ResendService {

    private static final int CHUNK_SIZE = 500;
    private static final int CONCURRENCY = 20;

    private final NotificationEventRepository eventRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final JavaMailSender mailhogMailSender;

    @Value("${mail.from.email:noreply@notifications-service.local}")
    private String fromEmail;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ResendStatus currentStatus;

    public ResendService(NotificationEventRepository eventRepository,
                         NotificationPreferenceRepository preferenceRepository,
                         @Qualifier("mailhogMailSender") JavaMailSender mailhogMailSender) {
        this.eventRepository = eventRepository;
        this.preferenceRepository = preferenceRepository;
        this.mailhogMailSender = mailhogMailSender;
    }

    public ResendStatus start(int limit) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A resend job is already running");
        }
        ResendStatus status = new ResendStatus(limit);
        currentStatus = status;
        Thread thread = new Thread(() -> {
            try {
                run(limit, status);
            } catch (Exception e) {
                log.error("Resend job failed unexpectedly", e);
                status.markFailed(e.getMessage());
            } finally {
                running.set(false);
            }
        }, "resend-main");
        thread.setDaemon(true);
        thread.start();
        return status;
    }

    public ResendStatus getStatus() {
        return currentStatus;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void run(int limit, ResendStatus status) throws InterruptedException {
        log.info("Resend job starting: limit={}", limit);

        List<NotificationEvent> events = eventRepository.findUnresent(PageRequest.of(0, limit));
        int actual = events.size();
        status.setActual(actual);
        status.markStarted();

        log.info("Found {} unresent notifications (requested limit={})", actual, limit);

        if (actual == 0) {
            status.markCompleted();
            log.info("No unresent notifications found — job complete");
            return;
        }

        int taskCount = (actual + CHUNK_SIZE - 1) / CHUNK_SIZE;
        int threads = Math.min(CONCURRENCY, taskCount);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(taskCount);

        try {
            for (int t = 0; t < taskCount; t++) {
                final int from = t * CHUNK_SIZE;
                final int to = Math.min(from + CHUNK_SIZE, actual);
                final List<NotificationEvent> chunk = new ArrayList<>(events.subList(from, to));
                pool.submit(() -> {
                    try {
                        processChunk(chunk, status);
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
        log.info("  RESEND JOB COMPLETE");
        log.info("  Notifications : {}/{} sent ({} failed)",
                status.getSent(), actual, status.getFailed());
        log.info("  Duration      : {}.{}s", elapsedMs / 1000, String.format("%03d", elapsedMs % 1000));
        log.info("  Throughput    : {}/s", String.format("%.1f", status.getThroughputPerSecond()));
        log.info("============================================================");
    }

    private void processChunk(List<NotificationEvent> events, ResendStatus status) {
        List<Long> ids = new ArrayList<>(events.size());
        List<SimpleMailMessage> messages = new ArrayList<>(events.size());

        for (NotificationEvent event : events) {
            String email = resolveEmail(event.getMemberId());
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(email);
            msg.setSubject(event.getTitle());
            msg.setText(event.getDescription() != null ? event.getDescription() : event.getTitle());
            messages.add(msg);
            ids.add(event.getId());
        }

        try {
            // Send entire chunk over a single SMTP connection to avoid ephemeral port exhaustion
            mailhogMailSender.send(messages.toArray(new SimpleMailMessage[0]));
            status.addSent(messages.size());
            eventRepository.markAsResent(ids);
        } catch (Exception e) {
            log.warn("Chunk send failed (size={}), falling back to per-message: {}", messages.size(), e.getMessage());
            sendIndividually(events, status);
        }
    }

    private void sendIndividually(List<NotificationEvent> events, ResendStatus status) {
        List<Long> sentIds = new ArrayList<>();
        for (NotificationEvent event : events) {
            try {
                String email = resolveEmail(event.getMemberId());
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromEmail);
                msg.setTo(email);
                msg.setSubject(event.getTitle());
                msg.setText(event.getDescription() != null ? event.getDescription() : event.getTitle());
                mailhogMailSender.send(msg);
                sentIds.add(event.getId());
                status.addSent(1);
            } catch (Exception e) {
                log.warn("Failed to resend event {}: {}", event.getId(), e.getMessage());
                status.addFailed(1);
            }
        }
        if (!sentIds.isEmpty()) {
            eventRepository.markAsResent(sentIds);
        }
    }

    private String resolveEmail(String memberId) {
        return preferenceRepository.findByMemberId(memberId)
                .map(p -> p.getEmail() != null && !p.getEmail().isBlank()
                        ? p.getEmail()
                        : memberId + "@mailhog.local")
                .orElse(memberId + "@mailhog.local");
    }

    // -------------------------------------------------------------------------

    public static class ResendStatus {

        private final int limit;
        private volatile int actual = 0;
        private final AtomicLong sent = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
        private volatile String state = "PENDING";
        private volatile long startMs = 0;
        private volatile long endMs = 0;
        private volatile String errorMessage;

        ResendStatus(int limit) {
            this.limit = limit;
        }

        void setActual(int actual) { this.actual = actual; }

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

        void addSent(long n)   { sent.addAndGet(n); }
        void addFailed(long n) { failed.addAndGet(n); }

        public int    getLimit()        { return limit; }
        public int    getActual()       { return actual; }
        public long   getSent()         { return sent.get(); }
        public long   getFailed()       { return failed.get(); }
        public String getState()        { return state; }
        public String getErrorMessage() { return errorMessage; }

        public double getProgressPercent() {
            return actual == 0 ? 0 : (sent.get() + failed.get()) * 100.0 / actual;
        }

        public double getThroughputPerSecond() {
            long elapsed = elapsedMs();
            return elapsed <= 0 ? 0 : sent.get() * 1000.0 / elapsed;
        }

        public long getElapsedMs() { return elapsedMs(); }

        private long elapsedMs() {
            if (startMs == 0) return 0;
            return (endMs > 0 ? endMs : System.currentTimeMillis()) - startMs;
        }
    }
}
