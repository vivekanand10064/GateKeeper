
package in.vs.gatekeeper.management.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import in.vs.gatekeeper.management.dto.eventDto.GatewayAccessEventMessage;
import in.vs.gatekeeper.management.service.GatewayAccessEventProcessingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayAccessEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAccessEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final GatewayAccessEventProcessingService processingService;

    public GatewayAccessEventConsumer(
            ObjectMapper objectMapper,
            GatewayAccessEventProcessingService processingService) {

        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

    @KafkaListener(
            topics = "${gateway.kafka.access-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "usageKafkaListenerContainerFactory"
    )
    public void consume(String message) {

        try {
            GatewayAccessEventMessage event =
                    objectMapper.readValue(
                            message,
                            GatewayAccessEventMessage.class
                    );

            boolean processed = processingService.processEvent(event);

            if (processed) {
                log.info(
                        "Gateway usage event processed: eventId={}, organizationId={}, status={}",
                        event.eventId(),
                        event.organizationId(),
                        event.responseStatus()
                );
            } else {
                log.debug(
                        "Duplicate gateway event ignored: eventId={}",
                        event.eventId()
                );
            }

        } catch (JacksonException exception) {
            log.error("Failed to deserialize gateway access event", exception);
            throw new IllegalStateException(
                    "Invalid gateway access event message", exception
            );
        }
    }
}
