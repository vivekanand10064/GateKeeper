
package in.vs.gatekeeper.analytics.dto.responseDto;

public record RouteAnalyticsResponse(
        String routeId,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double averageResponseTimeMs,
        double errorRatePercent,
        String healthStatus,
        String healthMessage
) {
}
