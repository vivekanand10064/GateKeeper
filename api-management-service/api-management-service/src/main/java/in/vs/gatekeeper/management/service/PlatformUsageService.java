
package in.vs.gatekeeper.management.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;
import in.vs.gatekeeper.management.entity.OrganizationStatus;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.repository.OrganizationMonthlyUsageRepository;
import in.vs.gatekeeper.management.repository.OrganizationRepository;
import in.vs.gatekeeper.management.repository.OrganizationUsagePlanRepository;

@Service
@Transactional(readOnly = true)
public class PlatformUsageService {

    private static final long DEFAULT_MONTHLY_LIMIT = 100000L;
    private static final BigDecimal DEFAULT_PRICE = BigDecimal.ZERO;
    private static final String DEFAULT_CURRENCY = "INR";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMonthlyUsageRepository monthlyUsageRepository;
    private final OrganizationUsagePlanRepository usagePlanRepository;

    public PlatformUsageService(
            OrganizationRepository organizationRepository,
            OrganizationMonthlyUsageRepository monthlyUsageRepository,
            OrganizationUsagePlanRepository usagePlanRepository) {

        this.organizationRepository = organizationRepository;
        this.monthlyUsageRepository = monthlyUsageRepository;
        this.usagePlanRepository = usagePlanRepository;
    }

    public List<OrganizationUsageReport> getOrganizationUsage(YearMonth month) {

        if (month == null) {
            throw new IllegalArgumentException("Billing month is required");
        }

        LocalDate billingMonth = month.atDay(1);

        List<Organization> organizations =
                organizationRepository.findAllByStatusNot(OrganizationStatus.DELETED);

        List<OrganizationUsageReport> reports = new ArrayList<>();

        for (Organization organization : organizations) {

            OrganizationMonthlyUsage usage =
                    monthlyUsageRepository
                            .findByOrganizationIdAndBillingMonth(
                                    organization.getId(), billingMonth)
                            .orElse(null);

            OrganizationUsagePlan plan =
                    usagePlanRepository
                            .findByOrganizationId(organization.getId())
                            .orElse(null);

            reports.add(buildReport(organization, usage, plan, billingMonth));
        }

        return reports;
    }

    public List<OrganizationUsageReport> getOrganizationHistory(Long organizationId) {

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Invalid organization ID");
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Organization not found"));

        OrganizationUsagePlan plan =
                usagePlanRepository.findByOrganizationId(organizationId)
                        .orElse(null);

        List<OrganizationMonthlyUsage> usageHistory =
                monthlyUsageRepository
                        .findAllByOrganizationIdOrderByBillingMonthDesc(organizationId);

        List<OrganizationUsageReport> reports = new ArrayList<>();

        for (OrganizationMonthlyUsage usage : usageHistory) {
            reports.add(buildReport(
                    organization, usage, plan, usage.getBillingMonth()));
        }

        return reports;
    }

    private OrganizationUsageReport buildReport(
            Organization organization,
            OrganizationMonthlyUsage usage,
            OrganizationUsagePlan plan,
            LocalDate billingMonth) {

        long totalRequests = usage != null && usage.getTotalRequests() != null
                ? usage.getTotalRequests() : 0L;

        long successfulRequests = usage != null && usage.getSuccessfulRequests() != null
                ? usage.getSuccessfulRequests() : 0L;

        long clientErrors = usage != null && usage.getClientErrorRequests() != null
                ? usage.getClientErrorRequests() : 0L;

        long serverErrors = usage != null && usage.getServerErrorRequests() != null
                ? usage.getServerErrorRequests() : 0L;

        long monthlyLimit = plan != null && plan.getMonthlyRequestLimit() != null
                ? plan.getMonthlyRequestLimit() : DEFAULT_MONTHLY_LIMIT;

        BigDecimal pricePer1000 = plan != null
                && plan.getPricePer1000Requests() != null
                ? plan.getPricePer1000Requests() : DEFAULT_PRICE;

        String currency = plan != null && plan.getCurrency() != null
                ? plan.getCurrency() : DEFAULT_CURRENCY;

        boolean quotaEnabled = plan == null || Boolean.TRUE.equals(plan.getEnabled());

        long remainingRequests = Math.max(0L, monthlyLimit - totalRequests);

        double usagePercentage = monthlyLimit > 0
                ? (totalRequests * 100.0) / monthlyLimit : 0.0;

        boolean quotaExceeded = quotaEnabled && totalRequests >= monthlyLimit;

        BigDecimal estimatedCost = BigDecimal.valueOf(totalRequests)
                .multiply(pricePer1000)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

        return new OrganizationUsageReport(
                organization.getId(),
                organization.getName(),
                billingMonth,
                totalRequests,
                successfulRequests,
                clientErrors,
                serverErrors,
                monthlyLimit,
                remainingRequests,
                usagePercentage,
                quotaExceeded,
                quotaEnabled,
                estimatedCost,
                currency
        );
    }

    public record OrganizationUsageReport(
            Long organizationId,
            String organizationName,
            LocalDate billingMonth,
            long totalRequests,
            long successfulRequests,
            long clientErrorRequests,
            long serverErrorRequests,
            long monthlyLimit,
            long remainingRequests,
            double usagePercentage,
            boolean quotaExceeded,
            boolean quotaEnabled,
            BigDecimal estimatedCost,
            String currency
    ) {}
}
