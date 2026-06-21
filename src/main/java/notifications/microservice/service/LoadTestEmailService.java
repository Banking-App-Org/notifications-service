package notifications.microservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class LoadTestEmailService {

    private final JavaMailSender mailhogMailSender;

    @Value("${mail.from.email:loadtest@notifications-service.local}")
    private String fromEmail;

    public LoadTestEmailService(@Qualifier("mailhogMailSender") JavaMailSender mailhogMailSender) {
        this.mailhogMailSender = mailhogMailSender;
    }

    @Async("loadTestExecutor")
    public void runLoadTest(int count, String toAddress) {
        log.info("Load test started: {} emails → {}", count, toAddress);
        long startMs = System.currentTimeMillis();

        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        int threads = Math.min(50, count);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(fromEmail);
                    msg.setTo(toAddress);
                    msg.setSubject("Load Test #" + idx);
                    msg.setText("Notification body for email #" + idx);
                    mailhogMailSender.send(msg);
                    int n = sent.incrementAndGet();
                    if (n % 10_000 == 0) {
                        log.info("Load test progress: {}/{} sent", n, count);
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("Load test email #{} failed: {}", idx, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        pool.shutdown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Load test interrupted", e);
        }

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("Load test complete: {}/{} sent, {} failed in {}ms ({} emails/sec)",
                sent.get(), count, failed.get(), elapsed,
                elapsed > 0 ? (sent.get() * 1000L / elapsed) : 0);
    }
}
