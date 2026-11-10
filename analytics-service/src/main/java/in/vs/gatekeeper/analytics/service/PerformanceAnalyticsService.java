package in.vs.gatekeeper.analytics.service;

import java.time.LocalDateTime;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.analytics.dto.responseDto.PerformanceMetricsResponse;
import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.security.TenantContext;

@Service
public class PerformanceAnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final TenantContext tenantContext;

    public PerformanceAnalyticsService(
            MongoTemplate mongoTemplate,
            TenantContext tenantContext) {
        this.mongoTemplate = mongoTemplate;
        this.tenantContext = tenantContext;
    }

    public PerformanceMetricsResponse getPerformanceMetrics() {
        return getPerformanceMetrics(null, null);
    }

    public PerformanceMetricsResponse getPerformanceMetrics(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Long organizationId = tenantContext.getCurrentOrganizationId();

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("responseTimeMs").ne(null);

        if (startDate != null && endDate != null) {
            criteria.and("timestamp").gte(startDate).lte(endDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group()
                        .count().as("totalRequests")
                        .avg("responseTimeMs").as("averageResponseTimeMs")
                        .min("responseTimeMs").as("minimumResponseTimeMs")
                        .max("responseTimeMs").as("maximumResponseTimeMs")
        );

        Document result = mongoTemplate.aggregate(
                aggregation,
                AccessEvent.class,
                Document.class
        ).getUniqueMappedResult();

        if (result == null) {
            return new PerformanceMetricsResponse(0, 0.0, 0, 0);
        }

        Number totalRequests = result.get("totalRequests", Number.class);
        Number average = result.get("averageResponseTimeMs", Number.class);
        Number minimum = result.get("minimumResponseTimeMs", Number.class);
        Number maximum = result.get("maximumResponseTimeMs", Number.class);

        return new PerformanceMetricsResponse(
                totalRequests.longValue(),
                average.doubleValue(),
                minimum.longValue(),
                maximum.longValue()
        );
    }
}