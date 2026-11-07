
package in.vs.gatekeeper.management.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import in.vs.gatekeeper.management.dto.requestDto.OrganizationUsagePlanRequest;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.service.OrganizationUsagePlanService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/platform/organizations/{organizationId}/usage-plan")
public class PlatformUsagePlanController {

    private final OrganizationUsagePlanService usagePlanService;

    public PlatformUsagePlanController(
            OrganizationUsagePlanService usagePlanService) {
        this.usagePlanService = usagePlanService;
    }

    @GetMapping
    public ResponseEntity<UsagePlanResponse> getUsagePlan(
            @PathVariable Long organizationId) {

        OrganizationUsagePlan plan =
                usagePlanService.getUsagePlan(organizationId);

        return ResponseEntity.ok(toResponse(plan));
    }

    @PutMapping
    public ResponseEntity<UsagePlanResponse> updateUsagePlan(
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationUsagePlanRequest request) {

        OrganizationUsagePlan plan =
                usagePlanService.updateUsagePlan(organizationId, request);

        return ResponseEntity.ok(toResponse(plan));
    }

    private UsagePlanResponse toResponse(OrganizationUsagePlan plan) {
        return new UsagePlanResponse(
                plan.getId(),
                plan.getOrganizationId(),
                plan.getMonthlyRequestLimit(),
                plan.getPricePer1000Requests(),
                plan.getWarningThresholdPercent(),
                plan.getCurrency(),
                plan.getEnabled(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    public record UsagePlanResponse(
            Long id,
            Long organizationId,
            Long monthlyRequestLimit,
            BigDecimal pricePer1000Requests,
            Integer warningThresholdPercent,
            String currency,
            Boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
