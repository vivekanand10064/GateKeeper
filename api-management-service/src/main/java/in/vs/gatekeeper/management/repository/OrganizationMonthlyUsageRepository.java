
package in.vs.gatekeeper.management.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import in.vs.gatekeeper.management.entity.OrganizationMonthlyUsage;

@Repository
public interface OrganizationMonthlyUsageRepository
        extends JpaRepository<OrganizationMonthlyUsage, Long> {

    Optional<OrganizationMonthlyUsage> findByOrganizationIdAndBillingMonth(
            Long organizationId,
            LocalDate billingMonth
    );

    List<OrganizationMonthlyUsage> findAllByBillingMonth(
            LocalDate billingMonth
    );

    List<OrganizationMonthlyUsage> findAllByOrganizationIdOrderByBillingMonthDesc(
            Long organizationId
    );

    @Modifying
    @Query(value = """
            INSERT INTO organization_monthly_usage (
                organization_id,
                billing_month,
                total_requests,
                successful_requests,
                client_error_requests,
                server_error_requests
            )
            VALUES (
                :organizationId,
                :billingMonth,
                1,
                CASE WHEN :status >= 200 AND :status < 300 THEN 1 ELSE 0 END,
                CASE WHEN :status >= 400 AND :status < 500 THEN 1 ELSE 0 END,
                CASE WHEN :status >= 500 AND :status < 600 THEN 1 ELSE 0 END
            )
            ON DUPLICATE KEY UPDATE
                total_requests = total_requests + 1,
                successful_requests = successful_requests +
                    CASE WHEN :status >= 200 AND :status < 300 THEN 1 ELSE 0 END,
                client_error_requests = client_error_requests +
                    CASE WHEN :status >= 400 AND :status < 500 THEN 1 ELSE 0 END,
                server_error_requests = server_error_requests +
                    CASE WHEN :status >= 500 AND :status < 600 THEN 1 ELSE 0 END
            """, nativeQuery = true)
    int recordRequest(
            @Param("organizationId") Long organizationId,
            @Param("billingMonth") LocalDate billingMonth,
            @Param("status") Integer status
    );
}
