package in.vs.gatekeeper.management.dto.requestDto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The only usage-plan fields a tenant administrator may change.
 * Pricing, billing plan/cycle, subscription price, the quota maximum and the
 * quota on/off switch are platform-admin-only and are deliberately absent here.
 */
public record TenantUsagePlanRequest(

        @NotNull(message = "Monthly request limit is required")
        @Positive(message = "Monthly request limit must be greater than zero")
        Long monthlyRequestLimit,

        @NotNull(message = "Warning threshold is required")
        @Min(value = 1, message = "Warning threshold must be at least 1%")
        @Max(value = 100, message = "Warning threshold cannot exceed 100%")
        Integer warningThresholdPercent

) {}
