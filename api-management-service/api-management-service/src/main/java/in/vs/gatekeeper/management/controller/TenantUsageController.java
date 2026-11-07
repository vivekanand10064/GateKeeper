
package in.vs.gatekeeper.management.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.service.OrganizationUsageQuotaService;
import in.vs.gatekeeper.management.service.TenantContext;

@RestController
@RequestMapping("/api/tenant/usage")
public class TenantUsageController {

    private final TenantContext tenantContext;
    private final OrganizationUsageQuotaService quotaService;

    public TenantUsageController(
            TenantContext tenantContext,
            OrganizationUsageQuotaService quotaService) {

        this.tenantContext = tenantContext;
        this.quotaService = quotaService;
    }

    @GetMapping("/current")
    public ResponseEntity<OrganizationUsageQuotaService.QuotaSnapshot>
            getCurrentUsage() {

        Organization organization = tenantContext.getOrganization();

        return ResponseEntity.ok(
                quotaService.getQuotaSnapshot(organization.getId())
        );
    }
}
