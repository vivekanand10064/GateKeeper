package in.vs.gatekeeper.management.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.vs.gatekeeper.management.dto.requestDto.TenantUsagePlanRequest;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.service.OrganizationUsagePlanService;
import jakarta.validation.Valid;

/**
 * Tenant view of its own usage plan.
 * Only monthlyRequestLimit (within the admin-defined maximum) and
 * warningThresholdPercent can be updated; everything else is read-only.
 */
@RestController
@RequestMapping("/api/tenant/usage-plan")
public class TenantUsagePlanController {

    private final OrganizationUsagePlanService usagePlanService;

    public TenantUsagePlanController(
            OrganizationUsagePlanService usagePlanService) {
        this.usagePlanService = usagePlanService;
    }

    @GetMapping
    public ResponseEntity<TenantUsagePlanResponse> getOwnUsagePlan() {
        return ResponseEntity.ok(
                toResponse(usagePlanService.getOwnUsagePlan()));
    }

    @PutMapping
    public ResponseEntity<TenantUsagePlanResponse> updateOwnUsagePlan(
            @Valid @RequestBody TenantUsagePlanRequest request) {

        return ResponseEntity.ok(
                toResponse(usagePlanService.updateOwnUsagePlan(request)));
    }

    private TenantUsagePlanResponse toResponse(OrganizationUsagePlan plan) {
        return new TenantUsagePlanResponse(
                plan.getMonthlyRequestLimit(),
                plan.getMaxMonthlyRequestLimit(),
                plan.getWarningThresholdPercent(),
                plan.getEnabled(),
                plan.getCurrency(),
                plan.getBillingPlan(),
                plan.getBillingCycle(),
                plan.getPricePer1000Requests(),
                plan.getMonthlySubscriptionPrice(),
                plan.getUpdatedAt()
        );
    }

    public record TenantUsagePlanResponse(
            // Editable by the tenant
            Long monthlyRequestLimit,
            // Read-only: set by the platform administrator
            Long maxMonthlyRequestLimit,
            // Editable by the tenant
            Integer warningThresholdPercent,
            // Read-only from here on
            Boolean quotaEnabled,
            String currency,
            String billingPlan,
            String billingCycle,
            BigDecimal pricePer1000Requests,
            BigDecimal monthlySubscriptionPrice,
            LocalDateTime updatedAt
    ) {}
}
