
package in.vs.gatekeeper.gateway.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record GatewayAccessEvent(
        String eventId,
        Long organizationId,
        String routeId,
        String requestMethod,
        String requestPath,
        Integer responseStatus,
        Long responseTimeMs,
        LocalDateTime timestamp,
        String clientIp,
        String userAgent,
        boolean billable
) {

    // Maintains compatibility with existing ProxyController calls.
    // Existing events remain billable by default.
    public GatewayAccessEvent(
            Long organizationId,
            String routeId,
            String requestMethod,
            String requestPath,
            Integer responseStatus,
            Long responseTimeMs,
            LocalDateTime timestamp,
            String clientIp,
            String userAgent
    ) {
        this(
                UUID.randomUUID().toString(),
                organizationId,
                routeId,
                requestMethod,
                requestPath,
                responseStatus,
                responseTimeMs,
                timestamp,
                clientIp,
                userAgent,
                true
        );
    }
}
