package notifications.microservice.service;

import notifications.microservice.dto.BankingEvent;
import notifications.microservice.entity.NotificationEvent;
import notifications.microservice.entity.NotificationPreference;
import notifications.microservice.repository.NotificationEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationsService {
    private final NotificationEventRepository notificationEventRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationMetricsService metricsService;
    private final SmsService smsService;
    private final EmailService emailService;

    @Autowired
    public NotificationsService(NotificationEventRepository notificationEventRepository,
                               NotificationPreferenceService preferenceService,
                               NotificationMetricsService metricsService,
                               SmsService smsService,
                               EmailService emailService) {
        this.notificationEventRepository = notificationEventRepository;
        this.preferenceService = preferenceService;
        this.metricsService = metricsService;
        this.smsService = smsService;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "notification-events", containerFactory = "bankingKafkaListenerContainerFactory")
    public void handleBankingEvent(BankingEvent event,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Received banking event: type={}, userId={}", event.getEventType(), event.getUserId());

        if (event.getEventType() == null) {
            log.warn("Banking event has no event_type, skipping");
            return;
        }

        switch (event.getEventType()) {
            case "USER_REGISTERED":
                handleUserRegistered(event);
                break;
            case "USER_UPDATED":
                handleUserUpdated(event);
                break;
            case "ACCOUNT_OPENED":
                handleAccountOpened(event);
                break;
            case "ACCOUNT_CLOSED":
                handleAccountClosed(event);
                break;
            case "ACCOUNT_FROZEN":
                handleAccountFrozen(event);
                break;
            case "DEPOSIT_COMPLETED":
                handleDepositCompleted(event);
                break;
            case "WITHDRAWAL_COMPLETED":
                handleWithdrawalCompleted(event);
                break;
            case "TRANSFER_COMPLETED":
                handleTransferCompleted(event);
                break;
            case "TRANSFER_FAILED":
                handleTransferFailed(event);
                break;
            case "PAYMENT_SCHEDULED":
                handlePaymentScheduled(event);
                break;
            case "PAYMENT_PROCESSED":
                handlePaymentProcessed(event);
                break;
            case "PAYMENT_FAILED":
                handlePaymentFailed(event);
                break;
            default:
                log.warn("No handler for banking event type: {}", event.getEventType());
        }
    }

    private void handleUserRegistered(BankingEvent event) {
        String userId = event.getUserId();
        String displayName = event.getFirstName() != null ? event.getFirstName() : userId;
        String title = "Welcome to Banking Portal!";
        String description = "Your account has been successfully created. Welcome, " + displayName + "!";

        metricsService.recordRegistration();

        NotificationPreference prefs = preferenceService.getOrCreatePreferencesWithContact(
                userId, event.getEmail(), event.getPhoneNumber());

        NotificationEvent auditEvent = new NotificationEvent();
        auditEvent.setMemberId(userId);
        auditEvent.setTitle(title);
        auditEvent.setDescription(description);
        auditEvent.setEventType("USER_REGISTERED");
        auditEvent.setRetryCount(0);
        notificationEventRepository.save(auditEvent);

        StringBuilder metadata = new StringBuilder("{");

        boolean emailAttempted = false;
        boolean emailSent = false;
        String email = (prefs.getEmail() != null && !prefs.getEmail().isBlank())
                ? prefs.getEmail() : event.getEmail();
        if (Boolean.TRUE.equals(prefs.getEmailEnabled()) && email != null && !email.isBlank()) {
            emailAttempted = true;
            emailSent = emailService.send(email, title, description);
        }
        metadata.append("\"emailEnabled\":").append(Boolean.TRUE.equals(prefs.getEmailEnabled()))
                .append(",\"emailAttempted\":").append(emailAttempted)
                .append(",\"emailSent\":").append(emailSent);

        boolean smsAttempted = false;
        boolean smsSent = false;
        String phone = (prefs.getPhoneNumber() != null && !prefs.getPhoneNumber().isBlank())
                ? prefs.getPhoneNumber() : event.getPhoneNumber();
        if (Boolean.TRUE.equals(prefs.getSmsEnabled()) && phone != null && !phone.isBlank()) {
            smsAttempted = true;
            smsSent = smsService.send(phone, title + "\n" + description);
        }
        metadata.append(",\"smsEnabled\":").append(Boolean.TRUE.equals(prefs.getSmsEnabled()))
                .append(",\"smsAttempted\":").append(smsAttempted)
                .append(",\"smsSent\":").append(smsSent);

        metadata.append("}");

        auditEvent.setStatus("DELIVERED");
        auditEvent.setDeliveredAt(LocalDateTime.now());
        auditEvent.setMetadata(metadata.toString());

        if ((emailAttempted && !emailSent) || (smsAttempted && !smsSent)) {
            auditEvent.setErrorMessage("External delivery partially or fully failed. Check logs.");
        }

        notificationEventRepository.save(auditEvent);
        log.info("USER_REGISTERED notification processed - userId: {}, emailSent: {}, smsSent: {}",
                userId, emailSent, smsSent);
    }

    private void handleUserUpdated(BankingEvent event) {
        String userId = event.getUserId();
        String title = "Profile Updated";
        String description = "Your account details have been successfully updated.";

        NotificationPreference prefs = preferenceService.syncContactDetails(
                userId, event.getEmail(), event.getPhoneNumber());

        NotificationEvent auditEvent = new NotificationEvent();
        auditEvent.setMemberId(userId);
        auditEvent.setTitle(title);
        auditEvent.setDescription(description);
        auditEvent.setEventType("USER_UPDATED");
        auditEvent.setRetryCount(0);
        notificationEventRepository.save(auditEvent);

        StringBuilder metadata = new StringBuilder("{");

        boolean emailAttempted = false;
        boolean emailSent = false;
        String email = (prefs.getEmail() != null && !prefs.getEmail().isBlank())
                ? prefs.getEmail() : event.getEmail();
        if (Boolean.TRUE.equals(prefs.getEmailEnabled()) && email != null && !email.isBlank()) {
            emailAttempted = true;
            emailSent = emailService.send(email, title, description);
        }
        metadata.append("\"emailEnabled\":").append(Boolean.TRUE.equals(prefs.getEmailEnabled()))
                .append(",\"emailAttempted\":").append(emailAttempted)
                .append(",\"emailSent\":").append(emailSent);

        boolean smsAttempted = false;
        boolean smsSent = false;
        String phone = (prefs.getPhoneNumber() != null && !prefs.getPhoneNumber().isBlank())
                ? prefs.getPhoneNumber() : event.getPhoneNumber();
        if (Boolean.TRUE.equals(prefs.getSmsEnabled()) && phone != null && !phone.isBlank()) {
            smsAttempted = true;
            smsSent = smsService.send(phone, title + "\n" + description);
        }
        metadata.append(",\"smsEnabled\":").append(Boolean.TRUE.equals(prefs.getSmsEnabled()))
                .append(",\"smsAttempted\":").append(smsAttempted)
                .append(",\"smsSent\":").append(smsSent);

        metadata.append("}");

        auditEvent.setStatus("DELIVERED");
        auditEvent.setDeliveredAt(LocalDateTime.now());
        auditEvent.setMetadata(metadata.toString());

        if ((emailAttempted && !emailSent) || (smsAttempted && !smsSent)) {
            auditEvent.setErrorMessage("External delivery partially or fully failed. Check logs.");
        }

        notificationEventRepository.save(auditEvent);
        log.info("USER_UPDATED notification processed - userId: {}, emailSent: {}, smsSent: {}",
                userId, emailSent, smsSent);
    }

    private void handleAccountOpened(BankingEvent event) {
        String accountNum = event.getAccountNumber() != null ? " (" + event.getAccountNumber() + ")" : "";
        sendBankingNotification(event,
                "Bank Account Opened",
                "Your new bank account" + accountNum + " has been successfully opened.",
                "ACCOUNT_OPENED");
    }

    private void handleAccountClosed(BankingEvent event) {
        String accountNum = event.getAccountNumber() != null ? " (" + event.getAccountNumber() + ")" : "";
        sendBankingNotification(event,
                "Bank Account Closed",
                "Your bank account" + accountNum + " has been closed.",
                "ACCOUNT_CLOSED");
    }

    private void handleAccountFrozen(BankingEvent event) {
        String accountNum = event.getAccountNumber() != null ? " (" + event.getAccountNumber() + ")" : "";
        sendBankingNotification(event,
                "Bank Account Frozen",
                "Your bank account" + accountNum + " has been frozen. Please contact support for assistance.",
                "ACCOUNT_FROZEN");
    }

    private void handleDepositCompleted(BankingEvent event) {
        String accountNum = event.getAccountNumber() != null ? " to account " + event.getAccountNumber() : "";
        sendBankingNotification(event,
                "Deposit Successful",
                "A deposit of " + formatAmount(event.getAmount(), event.getCurrency()) + " has been credited" + accountNum + ".",
                "DEPOSIT_COMPLETED");
    }

    private void handleWithdrawalCompleted(BankingEvent event) {
        String accountNum = event.getAccountNumber() != null ? " from account " + event.getAccountNumber() : "";
        sendBankingNotification(event,
                "Withdrawal Successful",
                "A withdrawal of " + formatAmount(event.getAmount(), event.getCurrency()) + " has been processed" + accountNum + ".",
                "WITHDRAWAL_COMPLETED");
    }

    private void handleTransferCompleted(BankingEvent event) {
        String payee = event.getPayee() != null ? " to " + event.getPayee() : "";
        sendBankingNotification(event,
                "Transfer Successful",
                "Your transfer of " + formatAmount(event.getAmount(), event.getCurrency()) + payee + " has been completed.",
                "TRANSFER_COMPLETED");
    }

    private void handleTransferFailed(BankingEvent event) {
        String payee = event.getPayee() != null ? " to " + event.getPayee() : "";
        sendBankingNotification(event,
                "Transfer Failed",
                "Your transfer of " + formatAmount(event.getAmount(), event.getCurrency()) + payee + " could not be completed. Please try again or contact support.",
                "TRANSFER_FAILED");
    }

    private void handlePaymentScheduled(BankingEvent event) {
        String payee = event.getPayee() != null ? " to " + event.getPayee() : "";
        sendBankingNotification(event,
                "Payment Scheduled",
                "A payment of " + formatAmount(event.getAmount(), event.getCurrency()) + payee + " has been scheduled.",
                "PAYMENT_SCHEDULED");
    }

    private void handlePaymentProcessed(BankingEvent event) {
        String payee = event.getPayee() != null ? " to " + event.getPayee() : "";
        sendBankingNotification(event,
                "Payment Processed",
                "Your payment of " + formatAmount(event.getAmount(), event.getCurrency()) + payee + " has been successfully processed.",
                "PAYMENT_PROCESSED");
    }

    private void handlePaymentFailed(BankingEvent event) {
        String payee = event.getPayee() != null ? " to " + event.getPayee() : "";
        sendBankingNotification(event,
                "Payment Failed",
                "Your payment of " + formatAmount(event.getAmount(), event.getCurrency()) + payee + " could not be processed. Please check your account or contact support.",
                "PAYMENT_FAILED");
    }

    public void sendBankingNotification(BankingEvent event, String title, String description, String eventType) {
        String userId = event.getUserId();
        NotificationPreference prefs = preferenceService.getOrCreatePreferences(userId);

        NotificationEvent auditEvent = new NotificationEvent();
        auditEvent.setMemberId(userId);
        auditEvent.setTitle(title);
        auditEvent.setDescription(description);
        auditEvent.setEventType(eventType);
        auditEvent.setRetryCount(0);
        notificationEventRepository.save(auditEvent);

        StringBuilder metadata = new StringBuilder("{");

        boolean emailAttempted = false;
        boolean emailSent = false;
        String email = (prefs.getEmail() != null && !prefs.getEmail().isBlank())
                ? prefs.getEmail() : event.getEmail();
        if (Boolean.TRUE.equals(prefs.getEmailEnabled()) && email != null && !email.isBlank()) {
            emailAttempted = true;
            emailSent = emailService.send(email, title, description);
        }
        metadata.append("\"emailEnabled\":").append(Boolean.TRUE.equals(prefs.getEmailEnabled()))
                .append(",\"emailAttempted\":").append(emailAttempted)
                .append(",\"emailSent\":").append(emailSent);

        boolean smsAttempted = false;
        boolean smsSent = false;
        String phone = (prefs.getPhoneNumber() != null && !prefs.getPhoneNumber().isBlank())
                ? prefs.getPhoneNumber() : event.getPhoneNumber();
        if (Boolean.TRUE.equals(prefs.getSmsEnabled()) && phone != null && !phone.isBlank()) {
            smsAttempted = true;
            smsSent = smsService.send(phone, title + "\n" + description);
        }
        metadata.append(",\"smsEnabled\":").append(Boolean.TRUE.equals(prefs.getSmsEnabled()))
                .append(",\"smsAttempted\":").append(smsAttempted)
                .append(",\"smsSent\":").append(smsSent);

        metadata.append("}");

        auditEvent.setStatus("DELIVERED");
        auditEvent.setDeliveredAt(LocalDateTime.now());
        auditEvent.setMetadata(metadata.toString());

        if ((emailAttempted && !emailSent) || (smsAttempted && !smsSent)) {
            auditEvent.setErrorMessage("External delivery partially or fully failed. Check logs.");
        }

        notificationEventRepository.save(auditEvent);
        log.info("{} notification processed - userId: {}, emailSent: {}, smsSent: {}",
                eventType, userId, emailSent, smsSent);
    }

    private String formatAmount(Double amount, String currency) {
        if (amount == null) return "";
        String curr = (currency != null && !currency.isBlank()) ? currency : "USD";
        return String.format("%.2f %s", amount, curr);
    }
}
