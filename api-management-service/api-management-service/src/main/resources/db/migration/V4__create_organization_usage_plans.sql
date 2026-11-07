
CREATE TABLE organization_usage_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,

    monthly_request_limit BIGINT NOT NULL DEFAULT 100000,

    price_per_1000_requests DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,

    warning_threshold_percent INT NOT NULL DEFAULT 80,

    currency VARCHAR(3) NOT NULL DEFAULT 'INR',

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_usage_plan_organization
        UNIQUE (organization_id),

    CONSTRAINT fk_usage_plan_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_monthly_request_limit
        CHECK (monthly_request_limit > 0),

    CONSTRAINT chk_warning_threshold
        CHECK (warning_threshold_percent BETWEEN 1 AND 100),

    CONSTRAINT chk_usage_price
        CHECK (price_per_1000_requests >= 0)
);

-- Create a default usage plan for existing organizations.
INSERT INTO organization_usage_plans (organization_id)
SELECT id
FROM organizations
WHERE status <> 'DELETED';
