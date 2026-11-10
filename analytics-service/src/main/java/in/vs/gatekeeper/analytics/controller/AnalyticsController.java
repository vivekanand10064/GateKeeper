
package in.vs.gatekeeper.analytics.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import in.vs.gatekeeper.analytics.dto.responseDto.PerformanceMetricsResponse;
import in.vs.gatekeeper.analytics.dto.responseDto.RouteAnalyticsResponse;
import in.vs.gatekeeper.analytics.dto.responseDto.TrafficAnalyticsResponse;
import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.service.AnalyticsService;
import in.vs.gatekeeper.analytics.service.PerformanceAnalyticsService;
import in.vs.gatekeeper.analytics.service.RouteAnalyticsService;
import in.vs.gatekeeper.analytics.service.SummaryAnalyticsService;
import in.vs.gatekeeper.analytics.service.TrafficAnalyticsService;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SummaryAnalyticsService summaryAnalyticsService;
    private final PerformanceAnalyticsService performanceAnalyticsService;
    private final RouteAnalyticsService routeAnalyticsService;
    private final TrafficAnalyticsService trafficAnalyticsService;

    public AnalyticsController(
            AnalyticsService analyticsService,
            SummaryAnalyticsService summaryAnalyticsService,
            PerformanceAnalyticsService performanceAnalyticsService,
            RouteAnalyticsService routeAnalyticsService,
            TrafficAnalyticsService trafficAnalyticsService) {

        this.analyticsService = analyticsService;
        this.summaryAnalyticsService = summaryAnalyticsService;
        this.performanceAnalyticsService = performanceAnalyticsService;
        this.routeAnalyticsService = routeAnalyticsService;
        this.trafficAnalyticsService = trafficAnalyticsService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<AccessEvent>> getAllEvents(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null && endDate == null) {
            return ResponseEntity.ok(analyticsService.getAllEvents());
        }

        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Please provide a valid start and end date."
            );
        }

        return ResponseEntity.ok(
                analyticsService.getEventsByDateRange(startDate, endDate)
        );
    }

    @GetMapping("/total")
    public ResponseEntity<Long> getTotalEvents() {
        return ResponseEntity.ok(analyticsService.getTotalEvents());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getRequestSummary(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null && endDate == null) {
            return ResponseEntity.ok(
                    summaryAnalyticsService.getRequestSummary()
            );
        }

        validateDateRange(startDate, endDate);

        return ResponseEntity.ok(
                summaryAnalyticsService.getRequestSummary(startDate, endDate)
        );
    }

    @GetMapping("/performance")
    public ResponseEntity<PerformanceMetricsResponse> getPerformanceMetrics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null && endDate == null) {
            return ResponseEntity.ok(
                    performanceAnalyticsService.getPerformanceMetrics()
            );
        }

        validateDateRange(startDate, endDate);

        return ResponseEntity.ok(
                performanceAnalyticsService.getPerformanceMetrics(startDate, endDate)
        );
    }

    @GetMapping("/routes")
    public ResponseEntity<List<RouteAnalyticsResponse>> getRouteAnalytics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null && endDate == null) {
            return ResponseEntity.ok(
                    routeAnalyticsService.getRouteAnalytics()
            );
        }

        validateDateRange(startDate, endDate);

        return ResponseEntity.ok(
                routeAnalyticsService.getRouteAnalytics(startDate, endDate)
        );
    }

    @GetMapping("/traffic")
    public ResponseEntity<TrafficAnalyticsResponse> getTrafficAnalytics(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {

        validateDateRange(startDate, endDate);

        return ResponseEntity.ok(
                trafficAnalyticsService.getTrafficAnalytics(startDate, endDate)
        );
    }

    private void validateDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null
                || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Please provide a valid start and end date."
            );
        }
    }
}
