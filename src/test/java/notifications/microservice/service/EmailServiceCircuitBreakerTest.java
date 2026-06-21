package notifications.microservice.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:29093", "port=29093"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:29093",
    "resilience4j.circuitbreaker.instances.emailService.minimum-number-of-calls=5",
    "resilience4j.circuitbreaker.instances.emailService.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.emailService.wait-duration-in-open-state=1s",
    "resilience4j.circuitbreaker.instances.emailService.permitted-number-of-calls-in-half-open-state=2"
})
class EmailServiceCircuitBreakerTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    @org.springframework.beans.factory.annotation.Qualifier("mailhogMailSender")
    private JavaMailSender mailhogMailSender;

    @BeforeEach
    void resetCircuit() {
        circuitBreakerRegistry.circuitBreaker("emailService").reset();
    }

    @Test
    void circuitOpensAfterRepeatedSMTPFailures() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // 5 consecutive failures → 100% failure rate, above the 50% threshold
        for (int i = 0; i < 5; i++) {
            emailService.send("user" + i + "@example.com", "Subject", "Body");
        }

        assertEquals(CircuitBreaker.State.OPEN,
                circuitBreakerRegistry.circuitBreaker("emailService").getState());
    }

    @Test
    void openCircuitSkipsSmtpAndReturnsFalse() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Open the circuit
        for (int i = 0; i < 5; i++) {
            emailService.send("user" + i + "@example.com", "Subject", "Body");
        }

        // Reset the mock so we can detect any unexpected calls after circuit opens
        reset(mailSender);

        boolean result = emailService.send("blocked@example.com", "Subject", "Body");

        assertFalse(result);
        verifyNoInteractions(mailSender); // fallback fired, SMTP was never called
    }

    @Test
    void circuitTransitionsToHalfOpenAfterWaitDuration() throws InterruptedException {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        for (int i = 0; i < 5; i++) {
            emailService.send("user" + i + "@example.com", "Subject", "Body");
        }

        // wait-duration-in-open-state is 1s in test config
        Thread.sleep(1500);

        assertEquals(CircuitBreaker.State.HALF_OPEN,
                circuitBreakerRegistry.circuitBreaker("emailService").getState());
    }

    @Test
    void circuitClosesAgainWhenProviderRecovers() throws InterruptedException {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Open the circuit
        for (int i = 0; i < 5; i++) {
            emailService.send("user" + i + "@example.com", "Subject", "Body");
        }

        // Wait for HALF_OPEN
        Thread.sleep(1500);

        // Provider recovers — stop throwing
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // permitted-number-of-calls-in-half-open-state=2, both succeed → circuit closes
        emailService.send("recovery1@example.com", "Subject", "Body");
        emailService.send("recovery2@example.com", "Subject", "Body");

        assertEquals(CircuitBreaker.State.CLOSED,
                circuitBreakerRegistry.circuitBreaker("emailService").getState());
    }
}