package in.vs.gatekeeper.management.dto.eventDto;

import java.time.LocalDateTime;

public record GatewayAccessEventMessage(
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
) {}