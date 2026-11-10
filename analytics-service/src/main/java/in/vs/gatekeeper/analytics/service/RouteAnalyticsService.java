
package in.vs.gatekeeper.analytics.service;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.analytics.dto.responseDto.RouteAnalyticsResponse;
import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.security.TenantContext;

@Service
public class RouteAnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final TenantContext tenantContext;

    public RouteAnalyticsService(
            MongoTemplate mongoTemplate,
            TenantContext tenantContext) {

        this.mongoTemplate = mongoTemplate;
        this.tenantContext = tenantContext;
    }

    public List<RouteAnalyticsResponse> getRouteAnalytics() {
        return getRouteAnalytics(null, null);
    }

    public List<RouteAnalyticsResponse> getRouteAnalytics(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Long organizationId = tenantContext.getCurrentOrganizationId();

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("routeId").ne(null);

        if (startDate != null && endDate != null) {
            criteria.and("timestamp").gte(startDate).lte(endDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("routeId")
                        .count().as("totalRequests")
                        .avg("responseTimeMs").as("averageResponseTimeMs"),
                Aggregation.sort(Sort.Direction.DESC, "totalRequests")
        );

        List<Document> results = mongoTemplate.aggregate(
                aggregation,
                AccessEvent.class,
                Document.class
        ).getMappedResults();

        return results.stream()
                .map(document -> mapToResponse(
                        document, organizationId, startDate, endDate))
                .toList();
    }

    private RouteAnalyticsResponse mapToResponse(
            Document document,
            Long organizationId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        String routeId = document.getString("_id");

        Number total = document.get("totalRequests", Number.class);

        long totalRequests = total == null ? 0L : total.longValue();

        long successfulRequests = countByStatus(
                organizationId, routeId, 200, 299, startDate, endDate);

        long failedRequests = countByStatus(
                organizationId, routeId, 400, 599, startDate, endDate);

        Number average = document.get(
                "averageResponseTimeMs", Number.class);

        double averageResponseTimeMs =
                average == null ? 0.0 : average.doubleValue();

        double errorRatePercent = totalRequests == 0
                ? 0.0
                : (failedRequests * 100.0) / totalRequests;

        String healthStatus = determineHealthStatus(
                errorRatePercent, averageResponseTimeMs);

        String healthMessage = determineHealthMessage(
                errorRatePercent, averageResponseTimeMs);

        return new RouteAnalyticsResponse(
                routeId,
                totalRequests,
                successfulRequests,
                failedRequests,
                averageResponseTimeMs,
                errorRatePercent,
                healthStatus,
                healthMessage
        );
    }

    private String determineHealthStatus(
            double errorRatePercent,
            double averageResponseTimeMs) {

        if (errorRatePercent >= 20.0) {
            return "CRITICAL";
        }

        if (errorRatePercent >= 5.0
                || averageResponseTimeMs > 1000.0) {
            return "WARNING";
        }

        return "HEALTHY";
    }

    private String determineHealthMessage(
            double errorRatePercent,
            double averageResponseTimeMs) {

        if (errorRatePercent >= 20.0) {
            return "Critical API error rate: "
                    + String.format("%.2f", errorRatePercent) + "%";
        }

        if (errorRatePercent >= 5.0
                && averageResponseTimeMs > 1000.0) {
            return "API error rate is elevated and response time is high";
        }

        if (errorRatePercent >= 5.0) {
            return "API error rate is above the healthy threshold";
        }

        if (averageResponseTimeMs > 1000.0) {
            return "API response time is higher than 1000 ms";
        }

        return "API is operating normally";
    }

    private long countByStatus(
            Long organizationId,
            String routeId,
            int minStatus,
            int maxStatus,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("routeId").is(routeId)
                .and("responseStatus").gte(minStatus).lte(maxStatus);

        if (startDate != null && endDate != null) {
            criteria.and("timestamp").gte(startDate).lte(endDate);
        }

        return mongoTemplate.count(
                new Query(criteria),
                AccessEvent.class
        );
    }
}
