package notifications.microservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending real Email notifications via Mailtrap SMTP.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from.email:afl.teslaru@gmail.com}")
    private String fromEmail;

    @Value("${mail.from.name:Notifications Service}")
    private String fromName;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @CircuitBreaker(name = "emailService", fallbackMethod = "fallbackSend")
    public boolean send(String toAddress, String subject, String body) {
        if (toAddress == null || toAddress.isBlank()) {
            log.warn("Email skipped: destination address is empty");
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromName + " <" + fromEmail + ">");
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Email sent via Mailtrap to {}", toAddress);
        return true;
    }

    public boolean fallbackSend(String toAddress, String subject, String body, Throwable t) {
        log.warn("Email circuit breaker — skipping delivery to {}: {}", toAddress, t.getMessage());
        return false;
    }
}
