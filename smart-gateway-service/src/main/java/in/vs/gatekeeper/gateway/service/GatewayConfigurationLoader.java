package in.vs.gatekeeper.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import in.vs.gatekeeper.gateway.client.ManagementApiClient;
import in.vs.gatekeeper.gateway.dto.GatewayRoute;

import java.util.List;

@Component
public class GatewayConfigurationLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigurationLoader.class);

    private final ManagementApiClient managementApiClient;
    private final ActiveRouteRegistry routeRegistry;

    public GatewayConfigurationLoader(ManagementApiClient managementApiClient,
                                      ActiveRouteRegistry routeRegistry) {
        this.managementApiClient = managementApiClient;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            refresh();
        } catch (Exception exception) {
            // The gateway can still start; routes are loaded again when
            // the management service publishes a configuration event.
            log.warn("Could not load initial gateway routes from the management service: {}",
                    exception.getMessage());
        }
    }

    /**
     * Periodically re-reads routes so the usage snapshot (currentMonthRequests,
     * used to re-seed Redis) does not stay frozen at whatever it was at the last
     * configuration change.
     */
    @Scheduled(
            fixedDelayString = "${gateway.config.refresh-interval-ms:60000}",
            initialDelayString = "${gateway.config.refresh-interval-ms:60000}")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception exception) {
            log.warn("Scheduled gateway route refresh failed: {}",
                    exception.getMessage());
        }
    }

    public void refresh() {
        List<GatewayRoute> routes = managementApiClient.fetchActiveRoutes();
        routeRegistry.replace(routes);
        log.info("Applied {} active gateway route(s)", routeRegistry.getRoutes().size());
    }
}
