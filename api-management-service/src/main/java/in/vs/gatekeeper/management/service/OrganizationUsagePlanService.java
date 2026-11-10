package in.vs.gatekeeper.management.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.dto.requestDto.OrganizationUsagePlanRequest;
import in.vs.gatekeeper.management.dto.requestDto.TenantUsagePlanRequest;
import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.entity.OrganizationStatus;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.event.GatewayConfigurationEventService;
import in.vs.gatekeeper.management.exception.ApiException;
import in.vs.gatekeeper.management.repository.OrganizationRepository;
import in.vs.gatekeeper.management.repository.OrganizationUsagePlanRepository;

@Service
public class OrganizationUsagePlanService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationUsagePlanRepository usagePlanRepository;
    private final GatewayConfigurationEventService configurationEventService;
    private final TenantContext tenantContext;
    private final AuditService auditService;

    public OrganizationUsagePlanService(
            OrganizationRepository organizationRepository,
            OrganizationUsagePlanRepository usagePlanRepository,
            GatewayConfigurationEventService configurationEventService,
            TenantContext tenantContext,
            AuditService auditService) {

        this.organizationRepository = organizationRepository;
        this.usagePlanRepository = usagePlanRepository;
        this.configurationEventService = configurationEventService;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
    }

    @Transactional
    public OrganizationUsagePlan updateUsagePlan(
            Long organizationId,
            OrganizationUsagePlanRequest request) {

        long startedAt = System.nanoTime();

        if (organizationId == null || organizationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid organization ID");
        }

        Organization organization = organizationRepository
                .findByIdAndStatusNot(organizationId, OrganizationStatus.DELETED)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found"));

        OrganizationUsagePlan plan = usagePlanRepository
                .findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    OrganizationUsagePlan newPlan = new OrganizationUsagePlan();
                    newPlan.setOrganizationId(organization.getId());
                    return newPlan;
                });

        plan.setMonthlyRequestLimit(request.monthlyRequestLimit());
        plan.setPricePer1000Requests(request.pricePer1000Requests());
        plan.setWarningThresholdPercent(request.warningThresholdPercent());
        plan.setCurrency(request.currency());
        plan.setEnabled(request.enabled());

        // Admin-only billing controls. null = leave unchanged.
        if (request.billingPlan() != null) {
            plan.setBillingPlan(request.billingPlan());
        }

        if (request.billingCycle() != null) {
            plan.setBillingCycle(request.billingCycle());
        }

        if (request.monthlySubscriptionPrice() != null) {
            plan.setMonthlySubscriptionPrice(request.monthlySubscriptionPrice());
        }

        if (request.maxMonthlyRequestLimit() != null) {
            plan.setMaxMonthlyRequestLimit(request.maxMonthlyRequestLimit());
        }

        if (plan.getMonthlyRequestLimit() > plan.getMaxMonthlyRequestLimit()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly request limit cannot exceed the organization "
                            + "quota maximum of "
                            + plan.getMaxMonthlyRequestLimit());
        }

        OrganizationUsagePlan saved = usagePlanRepository.save(plan);

        auditService.record(organization, "UPDATE_USAGE_PLAN",
                "USAGE_PLAN", saved.getId().toString(), startedAt,
                "Usage plan updated by platform administrator");

        // The gateway caches monthlyRequestLimit / quotaEnabled / warning
        // threshold inside its route snapshot. Notify it (after commit) so it
        // reloads, otherwise it keeps enforcing the old limit.
        configurationEventService.publish("USAGE_PLAN_UPDATED",
                organizationId, null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public OrganizationUsagePlan getUsagePlan(Long organizationId) {

        if (organizationId == null || organizationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid organization ID");
        }

        organizationRepository
                .findByIdAndStatusNot(organizationId, OrganizationStatus.DELETED)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found"));

        return usagePlanRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Usage plan not found"));
    }

    // ---- Tenant self-service (limited fields) ----

    @Transactional(readOnly = true)
    public OrganizationUsagePlan getOwnUsagePlan() {

        Organization organization = tenantContext.getOrganization();

        return usagePlanRepository.findByOrganizationId(organization.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Usage plan not found"));
    }

    /**
     * A tenant may change only its monthly request limit (never above the
     * platform-defined maximum) and its warning threshold.
     */
    @Transactional
    public OrganizationUsagePlan updateOwnUsagePlan(
            TenantUsagePlanRequest request) {

        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();

        OrganizationUsagePlan plan = usagePlanRepository
                .findByOrganizationId(organization.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Usage plan not found"));

        if (request.monthlyRequestLimit() > plan.getMaxMonthlyRequestLimit()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly request limit cannot exceed the maximum of "
                            + plan.getMaxMonthlyRequestLimit()
                            + " set by the platform administrator");
        }

        plan.setMonthlyRequestLimit(request.monthlyRequestLimit());
        plan.setWarningThresholdPercent(request.warningThresholdPercent());

        OrganizationUsagePlan saved = usagePlanRepository.save(plan);

        auditService.record(organization, "UPDATE_OWN_USAGE_PLAN",
                "USAGE_PLAN", saved.getId().toString(), startedAt,
                "Tenant updated monthly request limit to "
                        + saved.getMonthlyRequestLimit()
                        + " and warning threshold to "
                        + saved.getWarningThresholdPercent() + "%");

        // The gateway caches the limit and threshold in its route snapshot.
        configurationEventService.publish("USAGE_PLAN_UPDATED",
                organization.getId(), null, null);

        return saved;
    }
}