package in.vs.gatekeeper.gateway.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import in.vs.gatekeeper.gateway.service.GatewayConfigurationLoader;

@Component
public class ConfigurationEventConsumer {

    private final GatewayConfigurationLoader configurationLoader;

    public ConfigurationEventConsumer(GatewayConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    @KafkaListener(topics = "${gateway.kafka.configuration-topic}",
            groupId = "${gateway.kafka.configuration-group}")
    public void configurationChanged(String event) {
        System.out.println("Gateway configuration notification received: " + event);

        configurationLoader.refresh();

        System.out.println("Gateway configuration updated: " + event);
    }
}
