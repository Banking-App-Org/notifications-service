package notifications.microservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending real SMS notifications via Infobip.
 */
@Slf4j
@Service
public class SmsService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${infobip.api-key:}")
    private String apiKey;

    @Value("${infobip.base-url:}")
    private String baseUrl;

    @Value("${infobip.from-name:AppService}")
    private String fromName;

    private boolean enabled = false;

    @PostConstruct
    public void init() {
        if (!apiKey.isBlank() && !baseUrl.isBlank()) {
            enabled = true;
            log.info("Infobip SMS service initialized. Base URL: {}", baseUrl);
        } else {
            log.warn("Infobip SMS is DISABLED. Ensure infobip.api-key and infobip.base-url are configured.");
        }
    }

    /**
     * Send an SMS to the given phone number via Infobip API.
     *
     * @param toNumber  Phone number (international format)
     * @param body      Message text
     * @return true if sent, false if skipped/failed
     */
    @CircuitBreaker(name = "smsService", fallbackMethod = "fallbackSend")
    public boolean send(String toNumber, String body) {
        if (!enabled) {
            log.info("SMS skipped because Infobip is disabled -> to={}", toNumber);
            return false;
        }

        String destination = toNumber == null ? "" : toNumber.trim().replace(" ", "").replace("-", "");
        if (destination.isBlank()) {
            log.warn("SMS skipped: destination phone number is empty");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "App " + apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("from", fromName);
        message.put("destinations", Collections.singletonList(Collections.singletonMap("to", destination)));
        message.put("text", body);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", Collections.singletonList(message));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        String url = "https://" + baseUrl + "/sms/2/text/advanced";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("SMS request accepted by Infobip for {} with HTTP status {}",
                destination, response.getStatusCodeValue());
        return true;
    }

    public boolean fallbackSend(String toNumber, String body, Throwable t) {
        log.warn("SMS circuit breaker — skipping delivery to {}: {}", toNumber, t.getMessage());
        return false;
    }
}
