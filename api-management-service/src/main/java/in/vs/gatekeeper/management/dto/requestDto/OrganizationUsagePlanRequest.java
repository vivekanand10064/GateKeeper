
package in.vs.gatekeeper.management.dto.requestDto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrganizationUsagePlanRequest(

        @NotNull(message = "Monthly request limit is required")
        @Positive(message = "Monthly request limit must be greater than zero")
        Long monthlyRequestLimit,

        @NotNull(message = "Price per 1000 requests is required")
        @DecimalMin(value = "0.0", message = "Price cannot be negative")
        BigDecimal pricePer1000Requests,

        @NotNull(message = "Warning threshold is required")
        @Min(value = 1, message = "Warning threshold must be at least 1%")
        @Max(value = 100, message = "Warning threshold cannot exceed 100%")
        Integer warningThresholdPercent,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$",
                message = "Currency must be a three-letter uppercase code")
        String currency,

        @NotNull(message = "Quota enabled status is required")
        Boolean enabled,

        // Optional admin-only billing controls. null = leave unchanged
        // (or use the default when the plan is created).

        @Size(max = 50, message = "Billing plan cannot exceed 50 characters")
        @Pattern(regexp = "^[A-Z0-9_]{2,50}$",
                message = "Billing plan must be 2-50 uppercase letters, digits or underscores")
        String billingPlan,

        @Pattern(regexp = "^(MONTHLY|QUARTERLY|YEARLY)$",
                message = "Billing cycle must be MONTHLY, QUARTERLY or YEARLY")
        String billingCycle,

        @DecimalMin(value = "0.0", message = "Subscription price cannot be negative")
        BigDecimal monthlySubscriptionPrice,

        @Positive(message = "Quota maximum must be greater than zero")
        Long maxMonthlyRequestLimit

) {}
