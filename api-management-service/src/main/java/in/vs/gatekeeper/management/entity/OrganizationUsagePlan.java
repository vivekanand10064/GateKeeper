
package in.vs.gatekeeper.management.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organization_usage_plans")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationUsagePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "monthly_request_limit", nullable = false)
    private Long monthlyRequestLimit = 100000L;

    @Column(name = "price_per_1000_requests", nullable = false, precision = 12, scale = 6)
    private BigDecimal pricePer1000Requests = BigDecimal.ZERO;

    @Column(name = "warning_threshold_percent", nullable = false)
    private Integer warningThresholdPercent = 80;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    private Boolean enabled = true;

    // ---- Platform-admin-only billing controls ----

    @Column(name = "billing_plan", nullable = false, length = 50)
    private String billingPlan = "STANDARD";

    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle = "MONTHLY";

    @Column(name = "monthly_subscription_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlySubscriptionPrice = BigDecimal.ZERO;

    // Organization quota maximum: tenants may set their own monthly limit
    // only up to this value.
    @Column(name = "max_monthly_request_limit", nullable = false)
    private Long maxMonthlyRequestLimit = 1000000L;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
