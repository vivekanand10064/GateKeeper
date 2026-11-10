package in.vs.gatekeeper.analytics.dto.responseDto;

public record PerformanceMetricsResponse(
        long totalRequests,
        double averageResponseTimeMs,
        long minimumResponseTimeMs,
        long maximumResponseTimeMs
) {
}