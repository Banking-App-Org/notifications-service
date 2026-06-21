package notifications.microservice.controller;

import lombok.extern.slf4j.Slf4j;
import notifications.microservice.service.NotificationSeederService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications/load-test")
public class LoadTestController {

    private final NotificationSeederService seederService;

    @Autowired
    public LoadTestController(NotificationSeederService seederService) {
        this.seederService = seederService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(
            @RequestParam(defaultValue = "100000") int count,
            @RequestParam(defaultValue = "20") int concurrency) {

        if (count < 1 || count > 1_000_000) {
            return ResponseEntity.badRequest().body(errorBody("count must be between 1 and 1,000,000"));
        }

        try {
            seederService.start(count);
            log.info("Seed job initiated: count={}", count);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Seed job started. Poll /status for progress.");
            response.put("count", count);
            response.put("statusUrl", "/api/notifications/load-test/status");
            response.put("cleanupUrl", "/api/notifications/load-test/cleanup");
            return ResponseEntity.accepted().body(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(errorBody(e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        NotificationSeederService.SeederStatus s = seederService.getStatus();

        if (s == null) {
            Map<String, Object> idle = new LinkedHashMap<>();
            idle.put("state", "IDLE");
            idle.put("message", "No seed job has been run yet");
            return ResponseEntity.ok(idle);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", s.getState());
        body.put("total", s.getTotal());
        body.put("completed", s.getCompleted());
        body.put("failed", s.getFailed());
        body.put("progressPercent", String.format("%.1f", s.getProgressPercent()));
        body.put("throughputPerSecond", String.format("%.1f", s.getThroughputPerSecond()));
        body.put("elapsedMs", s.getElapsedMs());
        if (s.getErrorMessage() != null) {
            body.put("errorMessage", s.getErrorMessage());
        }
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<?> cleanup() {
        if (seederService.isRunning()) {
            return ResponseEntity.status(409).body(errorBody("Cannot clean up while a seed job is running"));
        }
        int deleted = seederService.cleanup();
        log.info("Seeder cleanup: deleted {} rows", deleted);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", deleted);
        body.put("message", "Seeded data removed from notification_events");
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
