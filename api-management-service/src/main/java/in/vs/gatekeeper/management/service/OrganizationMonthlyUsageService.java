
package in.vs.gatekeeper.management.service;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;
import in.vs.gatekeeper.management.repository.OrganizationMonthlyUsageRepository;

@Service
public class OrganizationMonthlyUsageService {

    private final OrganizationMonthlyUsageRepository usageRepository;
    private final BillingClock billingClock;

    public OrganizationMonthlyUsageService(
            OrganizationMonthlyUsageRepository usageRepository,
            BillingClock billingClock) {
        this.billingClock = billingClock;
        this.usageRepository = usageRepository;
    }

    /**
     * Records one API request for an organization.
     */
    @Transactional
    public void recordRequest(Long organizationId, Integer responseStatus) {

        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Invalid organization ID.");
        }

        if (responseStatus == null || responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException("Invalid HTTP response status.");
        }

        LocalDate billingMonth = billingClock.currentBillingMonth();

        usageRepository.recordRequest(
                organizationId,
                billingMonth,
                responseStatus
        );
    }

    /**
     * Retrieves usage for the current billing month.
     */
    @Transactional(readOnly = true)
    public OrganizationMonthlyUsage getCurrentMonthUsage(Long organizationId) {

        LocalDate billingMonth = billingClock.currentBillingMonth();

        return usageRepository
                .findByOrganizationIdAndBillingMonth(
                        organizationId,
                        billingMonth
                )
                .orElse(null);
    }

    /**
     * Retrieves usage for a specified billing month.
     */
    @Transactional(readOnly = true)
    public OrganizationMonthlyUsage getMonthlyUsage(
            Long organizationId,
            YearMonth month) {

        if (month == null) {
            throw new IllegalArgumentException("Billing month is required.");
        }

        return usageRepository
                .findByOrganizationIdAndBillingMonth(
                        organizationId,
                        month.atDay(1)
                )
                .orElse(null);
    }
}
