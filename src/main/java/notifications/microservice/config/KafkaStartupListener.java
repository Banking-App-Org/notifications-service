package notifications.microservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaStartupListener {

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @EventListener(ApplicationReadyEvent.class)
    public void startKafkaListeners() {
        log.info("Application ready — starting Kafka listener containers");
        registry.start();
    }
}
