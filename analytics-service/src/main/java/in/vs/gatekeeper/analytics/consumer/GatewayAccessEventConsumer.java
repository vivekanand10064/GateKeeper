
package in.vs.gatekeeper.analytics.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.service.AnalyticsService;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayAccessEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAccessEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    public GatewayAccessEventConsumer(
            ObjectMapper objectMapper,
            AnalyticsService analyticsService) {
        this.objectMapper = objectMapper;
        this.analyticsService = analyticsService;
    }

    @KafkaListener(
            topics = "${gateway.kafka.access-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            AccessEvent event =
                    objectMapper.readValue(message, AccessEvent.class);

            analyticsService.saveEvent(event);

            log.info("Access event saved: routeId={}, status={}",
                    event.getRouteId(),
                    event.getResponseStatus());

        } catch (Exception exception) {
            log.error("Failed to process gateway access event", exception);
            throw new IllegalStateException(
                    "Unable to process gateway access event", exception);
        }
    }
}
