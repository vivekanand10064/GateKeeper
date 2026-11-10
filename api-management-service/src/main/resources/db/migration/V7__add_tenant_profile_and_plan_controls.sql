-- Tenant-editable profile fields (organization description and contact details).
ALTER TABLE organizations
    ADD COLUMN description VARCHAR(1000) NULL,
    ADD COLUMN contact_email VARCHAR(255) NULL,
    ADD COLUMN contact_phone VARCHAR(30) NULL;

-- Platform-admin-only billing controls on the usage plan.
--   billing_plan, billing_cycle, monthly_subscription_price,
--   price_per_1000_requests (already exists) and max_monthly_request_limit
--   (the organization quota maximum) may only be changed by the platform admin.
-- Tenants may change monthly_request_limit and warning_threshold_percent,
-- but monthly_request_limit may never exceed max_monthly_request_limit.
ALTER TABLE organization_usage_plans
    ADD COLUMN billing_plan VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN monthly_subscription_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN max_monthly_request_limit BIGINT NOT NULL DEFAULT 1000000;

-- Existing plans must already satisfy limit <= maximum.
UPDATE organization_usage_plans
SET max_monthly_request_limit = GREATEST(max_monthly_request_limit, monthly_request_limit);

ALTER TABLE organization_usage_plans
    ADD CONSTRAINT chk_usage_plan_billing_cycle
        CHECK (billing_cycle IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    ADD CONSTRAINT chk_usage_plan_subscription_price
        CHECK (monthly_subscription_price >= 0),
    ADD CONSTRAINT chk_usage_plan_max_limit
        CHECK (max_monthly_request_limit > 0),
    ADD CONSTRAINT chk_usage_plan_limit_within_max
        CHECK (monthly_request_limit <= max_monthly_request_limit);
