
package in.vs.gatekeeper.analytics.dto.responseDto;

import java.util.List;

public record TrafficAnalyticsResponse(

        long totalRequests,

        long requestsPerMinute,

        long requestsPerHour,

        String peakTrafficTime,

        long peakRequestCount,

        List<TrafficPoint> trafficTrend,

        List<RouteTraffic> routeTraffic,

        List<TrafficAlert> trafficAlerts

) {

    public record TrafficPoint(
            String time,
            long requestCount
    ) {
    }

    public record RouteTraffic(
            String routeId,
            long totalRequests,
            double trafficPercentage
    ) {
    }

    public record TrafficAlert(
            String routeId,
            String severity,
            String message,
            long requestCount
    ) {
    }
}
