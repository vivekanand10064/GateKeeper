package in.vs.gatekeeper.management.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.dto.requestDto.OrganizationUsagePlanRequest;
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

    public OrganizationUsagePlanService(
            OrganizationRepository organizationRepository,
            OrganizationUsagePlanRepository usagePlanRepository,
            GatewayConfigurationEventService configurationEventService) {

        this.organizationRepository = organizationRepository;
        this.usagePlanRepository = usagePlanRepository;
        this.configurationEventService = configurationEventService;
    }

    @Transactional
    public OrganizationUsagePlan updateUsagePlan(
            Long organizationId,
            OrganizationUsagePlanRequest request) {

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

        OrganizationUsagePlan saved = usagePlanRepository.save(plan);

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
}