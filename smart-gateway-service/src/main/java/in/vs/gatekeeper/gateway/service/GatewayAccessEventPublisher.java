
package in.vs.gatekeeper.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;
import in.vs.gatekeeper.gateway.dto.GatewayAccessEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GatewayAccessEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAccessEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String accessTopic;

    public GatewayAccessEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${gateway.kafka.access-topic}") String accessTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.accessTopic = accessTopic;
    }

    public CompletableFuture<SendResult<String, String>> publish(
            GatewayAccessEvent event) {

        try {
            String message = objectMapper.writeValueAsString(event);

            String key = event.organizationId() + ":" + event.routeId();

            return kafkaTemplate.send(accessTopic, key, message)
                    .whenComplete((result, exception) -> {

                        if (exception != null) {
                            log.error(
                                    "Failed to publish gateway access event. eventId={}, organizationId={}",
                                    event.eventId(),
                                    event.organizationId(),
                                    exception
                            );
                        } else {
                            log.info(
                                    "Gateway access event published. eventId={}, topic={}, partition={}, offset={}",
                                    event.eventId(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset()
                            );
                        }
                    });

        } catch (JacksonException exception) {
            log.error("Failed to serialize gateway access event", exception);

            throw new IllegalStateException(
                    "Could not serialize gateway access event",
                    exception
            );
        }
    }
}
