
CREATE TABLE organization_monthly_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,

    billing_month DATE NOT NULL,

    total_requests BIGINT NOT NULL DEFAULT 0,

    successful_requests BIGINT NOT NULL DEFAULT 0,

    client_error_requests BIGINT NOT NULL DEFAULT 0,

    server_error_requests BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_billing_month
        UNIQUE (organization_id, billing_month),

    CONSTRAINT fk_monthly_usage_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_usage_total
        CHECK (total_requests >= 0),

    CONSTRAINT chk_usage_successful
        CHECK (successful_requests >= 0),

    CONSTRAINT chk_usage_client_errors
        CHECK (client_error_requests >= 0),

    CONSTRAINT chk_usage_server_errors
        CHECK (server_error_requests >= 0)
);
