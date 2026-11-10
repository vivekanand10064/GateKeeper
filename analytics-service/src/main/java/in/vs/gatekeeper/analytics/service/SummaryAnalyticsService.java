package in.vs.gatekeeper.analytics.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.security.TenantContext;

@Service
public class SummaryAnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final AnalyticsService analyticsService;
    private final TenantContext tenantContext;

    public SummaryAnalyticsService(
            MongoTemplate mongoTemplate,
            AnalyticsService analyticsService,
            TenantContext tenantContext) {
        this.mongoTemplate = mongoTemplate;
        this.analyticsService = analyticsService;
        this.tenantContext = tenantContext;
    }

    public Map<String, Long> getRequestSummary() {
        return getRequestSummary(null, null);
    }

    public Map<String, Long> getRequestSummary(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Long organizationId = tenantContext.getCurrentOrganizationId();

        long totalRequests;
        if (startDate == null && endDate == null) {
            totalRequests = analyticsService.getTotalEvents();
        } else {
            totalRequests = countByDateRange(
                    organizationId, startDate, endDate);
        }

        long successfulRequests = countByStatus(
                organizationId, 200, 299, startDate, endDate);

        long clientErrors = countByStatus(
                organizationId, 400, 499, startDate, endDate);

        long serverErrors = countByStatus(
                organizationId, 500, 599, startDate, endDate);

        Map<String, Long> summary = new LinkedHashMap<>();

        summary.put("totalRequests", totalRequests);
        summary.put("successfulRequests", successfulRequests);
        summary.put("clientErrors", clientErrors);
        summary.put("serverErrors", serverErrors);
        summary.put("failedRequests", clientErrors + serverErrors);

        return summary;
    }

    private long countByDateRange(
            Long organizationId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Query query = new Query(
                Criteria.where("organizationId").is(organizationId)
                        .and("timestamp").gte(startDate).lte(endDate)
        );

        return mongoTemplate.count(query, AccessEvent.class);
    }

    private long countByStatus(
            Long organizationId,
            int minStatus,
            int maxStatus,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("responseStatus").gte(minStatus).lte(maxStatus);

        if (startDate != null && endDate != null) {
            criteria.and("timestamp").gte(startDate).lte(endDate);
        }

        return mongoTemplate.count(new Query(criteria), AccessEvent.class);
    }
}