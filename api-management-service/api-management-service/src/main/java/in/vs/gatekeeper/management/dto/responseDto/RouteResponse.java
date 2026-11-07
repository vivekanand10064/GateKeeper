
package in.vs.gatekeeper.management.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import in.vs.gatekeeper.management.entity.ApiRoute;
import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.entity.RouteHeaderRule;

@Getter
@AllArgsConstructor
public class RouteResponse {

    private Long id;
    private Long organizationId;
    private String routeId;
    private String name;
    private String pathPattern;
    private String targetBaseUrl;
    private String targetPathPrefix;
    private Integer requestsPerMinute;
    private Integer responseTimeoutMs;
    private boolean idempotencyEnabled;
    private boolean active;
    private Long configurationVersion;
    private Set<String> allowedMethods;
    private List<HeaderRuleResponse> headerRules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Monthly quota configuration
    private Long monthlyRequestLimit;
    private Long currentMonthRequests;
    private Integer warningThresholdPercent;
    private Boolean quotaEnabled;

    // Existing conversion method
    public static RouteResponse from(ApiRoute route) {

        return from(route, null, null);
    }

    // Conversion method with organization quota information
    public static RouteResponse from(
            ApiRoute route,
            OrganizationUsagePlan usagePlan,
            OrganizationMonthlyUsage monthlyUsage) {

        List<HeaderRuleResponse> rules = new ArrayList<>();

        for (RouteHeaderRule rule : route.getHeaderRules()) {
            rules.add(HeaderRuleResponse.from(rule));
        }

        Long monthlyLimit = usagePlan != null
                ? usagePlan.getMonthlyRequestLimit()
                : null;

        Long currentRequests = monthlyUsage != null
                ? monthlyUsage.getTotalRequests()
                : 0L;

        Integer warningThreshold = usagePlan != null
                ? usagePlan.getWarningThresholdPercent()
                : null;

        Boolean enabled = usagePlan != null
                ? usagePlan.getEnabled()
                : null;

        return new RouteResponse(
                route.getId(),
                route.getOrganization().getId(),
                route.getRouteId(),
                route.getName(),
                route.getPathPattern(),
                route.getTargetBaseUrl(),
                route.getTargetPathPrefix(),
                route.getRequestsPerMinute(),
                route.getResponseTimeoutMs(),
                route.isIdempotencyEnabled(),
                route.isActive(),
                route.getConfigurationVersion(),
                route.getAllowedMethods(),
                rules,
                route.getCreatedAt(),
                route.getUpdatedAt(),
                monthlyLimit,
                currentRequests,
                warningThreshold,
                enabled
        );
    }
}
