package notifications.microservice.controller;

import lombok.extern.slf4j.Slf4j;
import notifications.microservice.service.ResendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications/resend")
public class ResendController {

    private final ResendService resendService;

    @Autowired
    public ResendController(ResendService resendService) {
        this.resendService = resendService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam(defaultValue = "100000") int limit) {
        if (limit < 1 || limit > 1_000_000) {
            return ResponseEntity.badRequest().body(errorBody("limit must be between 1 and 1,000,000"));
        }
        try {
            resendService.start(limit);
            log.info("Resend job initiated: limit={}", limit);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Resend job started. Poll /status for progress.");
            response.put("limit", limit);
            response.put("statusUrl", "/api/notifications/resend/status");
            return ResponseEntity.accepted().body(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(errorBody(e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        ResendService.ResendStatus s = resendService.getStatus();
        if (s == null) {
            Map<String, Object> idle = new LinkedHashMap<>();
            idle.put("state", "IDLE");
            idle.put("message", "No resend job has been run yet");
            return ResponseEntity.ok(idle);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", s.getState());
        body.put("limit", s.getLimit());
        body.put("actual", s.getActual());
        body.put("sent", s.getSent());
        body.put("failed", s.getFailed());
        body.put("progressPercent", String.format("%.1f", s.getProgressPercent()));
        body.put("throughputPerSecond", String.format("%.1f", s.getThroughputPerSecond()));
        body.put("elapsedMs", s.getElapsedMs());
        if (s.getErrorMessage() != null) {
            body.put("errorMessage", s.getErrorMessage());
        }
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
