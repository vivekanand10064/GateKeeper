
package in.vs.gatekeeper.analytics.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.analytics.dto.responseDto.TrafficAnalyticsResponse;
import in.vs.gatekeeper.analytics.dto.responseDto.TrafficAnalyticsResponse.RouteTraffic;
import in.vs.gatekeeper.analytics.dto.responseDto.TrafficAnalyticsResponse.TrafficAlert;
import in.vs.gatekeeper.analytics.dto.responseDto.TrafficAnalyticsResponse.TrafficPoint;
import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.security.TenantContext;

@Service
public class TrafficAnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final TenantContext tenantContext;

    public TrafficAnalyticsService(
            MongoTemplate mongoTemplate,
            TenantContext tenantContext) {

        this.mongoTemplate = mongoTemplate;
        this.tenantContext = tenantContext;
    }

    public TrafficAnalyticsResponse getTrafficAnalytics(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null
                || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Please provide a valid date range."
            );
        }

        Long organizationId = tenantContext.getCurrentOrganizationId();

        Criteria criteria = Criteria.where("organizationId")
                .is(organizationId)
                .and("timestamp")
                .gte(startDate)
                .lte(endDate);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));

        // Spring Data maps MongoDB timestamps to LocalDateTime.
        List<AccessEvent> events = mongoTemplate.find(
                query,
                AccessEvent.class
        );

        long totalRequests = events.size();

        if (totalRequests == 0) {
            return new TrafficAnalyticsResponse(
                    0, 0, 0,
                    "No traffic",
                    0,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        LocalDateTime latestTime = events.stream()
                .map(AccessEvent::getTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(endDate);

        long requestsPerMinute = countSince(
                events,
                latestTime.truncatedTo(ChronoUnit.MINUTES)
        );

        long requestsPerHour = countSince(
                events,
                latestTime.truncatedTo(ChronoUnit.HOURS)
        );

        boolean minuteInterval =
                ChronoUnit.HOURS.between(startDate, endDate) <= 24;

        List<TrafficPoint> trafficTrend =
                buildTrafficTrend(events, minuteInterval);

        TrafficPoint peak = trafficTrend.stream()
                .max((a, b) -> Long.compare(
                        a.requestCount(),
                        b.requestCount()
                ))
                .orElse(new TrafficPoint("No traffic", 0));

        List<RouteTraffic> routeTraffic =
                buildRouteTraffic(events, totalRequests);

        List<TrafficAlert> trafficAlerts =
                buildTrafficAlerts(trafficTrend);

        return new TrafficAnalyticsResponse(
                totalRequests,
                requestsPerMinute,
                requestsPerHour,
                peak.time(),
                peak.requestCount(),
                trafficTrend,
                routeTraffic,
                trafficAlerts
        );
    }

    private long countSince(
            List<AccessEvent> events,
            LocalDateTime boundary) {

        return events.stream()
                .map(AccessEvent::getTimestamp)
                .filter(Objects::nonNull)
                .filter(timestamp -> !timestamp.isBefore(boundary))
                .count();
    }

    private List<TrafficPoint> buildTrafficTrend(
            List<AccessEvent> events,
            boolean minuteInterval) {

        Map<LocalDateTime, Long> buckets = new TreeMap<>();

        for (AccessEvent event : events) {

            LocalDateTime timestamp = event.getTimestamp();

            if (timestamp == null) {
                continue;
            }

            LocalDateTime bucket = minuteInterval
                    ? timestamp.truncatedTo(ChronoUnit.MINUTES)
                    : timestamp.truncatedTo(ChronoUnit.HOURS);

            buckets.merge(bucket, 1L, Long::sum);
        }

        return buckets.entrySet().stream()
                .map(entry -> new TrafficPoint(
                        entry.getKey().toString(),
                        entry.getValue()
                ))
                .toList();
    }

    private List<RouteTraffic> buildRouteTraffic(
            List<AccessEvent> events,
            long totalRequests) {

        Map<String, Long> counts = new HashMap<>();

        for (AccessEvent event : events) {

            String routeId = event.getRouteId();

            if (routeId != null) {
                counts.merge(routeId, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue(),
                        a.getValue()
                ))
                .map(entry -> new RouteTraffic(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue() * 100.0 / totalRequests
                ))
                .toList();
    }

    private List<TrafficAlert> buildTrafficAlerts(
            List<TrafficPoint> trend) {

        if (trend.size() < 2) {
            return List.of();
        }

        double average = trend.stream()
                .mapToLong(TrafficPoint::requestCount)
                .average()
                .orElse(0);

        if (average == 0) {
            return List.of();
        }

        List<TrafficAlert> alerts = new ArrayList<>();

        for (TrafficPoint point : trend) {

            if (point.requestCount() > average * 3
                    && point.requestCount() >= 5) {

                alerts.add(new TrafficAlert(
                        "Multiple routes",
                        "WARNING",
                        "Unusual traffic spike detected at "
                                + point.time(),
                        point.requestCount()
                ));
            }
        }

        return alerts;
    }
}
