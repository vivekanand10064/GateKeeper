
package in.vs.gatekeeper.management.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;
import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;
import in.vs.gatekeeper.management.repository.OrganizationMonthlyUsageRepository;
import in.vs.gatekeeper.management.repository.OrganizationUsagePlanRepository;

@Service
public class OrganizationUsageQuotaService {

    private final OrganizationUsagePlanRepository planRepository;
    private final OrganizationMonthlyUsageRepository usageRepository;

    public OrganizationUsageQuotaService(
            OrganizationUsagePlanRepository planRepository,
            OrganizationMonthlyUsageRepository usageRepository) {

        this.planRepository = planRepository;
        this.usageRepository = usageRepository;
    }

    @Transactional(readOnly = true)
    public QuotaSnapshot getQuotaSnapshot(Long organizationId) {

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Invalid organization ID.");
        }

        OrganizationUsagePlan plan = planRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Usage plan not found for organization."
                ));

        LocalDate billingMonth = YearMonth.now().atDay(1);

        OrganizationMonthlyUsage usage = usageRepository
                .findByOrganizationIdAndBillingMonth(
                        organizationId,
                        billingMonth
                )
                .orElse(null);

        long totalRequests = usage == null
                ? 0L
                : usage.getTotalRequests();

        long monthlyLimit = plan.getMonthlyRequestLimit();

        long remainingRequests = Math.max(
                0L,
                monthlyLimit - totalRequests
        );

        double usagePercentage = monthlyLimit > 0
                ? (totalRequests * 100.0) / monthlyLimit
                : 0.0;

        boolean quotaExceeded =
                Boolean.TRUE.equals(plan.getEnabled())
                && totalRequests >= monthlyLimit;

        boolean warningThresholdReached =
                Boolean.TRUE.equals(plan.getEnabled())
                && usagePercentage >= plan.getWarningThresholdPercent();

        BigDecimal estimatedCost = BigDecimal
                .valueOf(totalRequests)
                .multiply(plan.getPricePer1000Requests())
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

        return new QuotaSnapshot(
                organizationId,
                billingMonth,
                totalRequests,
                monthlyLimit,
                remainingRequests,
                Math.round(usagePercentage * 100.0) / 100.0,
                warningThresholdReached,
                quotaExceeded,
                Boolean.TRUE.equals(plan.getEnabled()),
                estimatedCost,
                plan.getCurrency()
        );
    }

    @Transactional(readOnly = true)
    public boolean isRequestAllowed(Long organizationId) {

        QuotaSnapshot snapshot = getQuotaSnapshot(organizationId);

        return !snapshot.quotaEnabled() || !snapshot.quotaExceeded();
    }

    public record QuotaSnapshot(
            Long organizationId,
            LocalDate billingMonth,
            long totalRequests,
            long monthlyLimit,
            long remainingRequests,
            double usagePercentage,
            boolean warningThresholdReached,
            boolean quotaExceeded,
            boolean quotaEnabled,
            BigDecimal estimatedCost,
            String currency
    ) {}
}
