
package in.vs.gatekeeper.management.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import in.vs.gatekeeper.management.service.BillingClock;
import in.vs.gatekeeper.management.service.PlatformUsageService;

@RestController
@RequestMapping("/api/platform/usage")
public class PlatformUsageController {

    private final PlatformUsageService platformUsageService;
    private final BillingClock billingClock;

    public PlatformUsageController(
            PlatformUsageService platformUsageService,
            BillingClock billingClock) {
        this.platformUsageService = platformUsageService;
        this.billingClock = billingClock;
    }

    // View usage across all organizations for a billing month.
    @GetMapping
    public ResponseEntity<List<PlatformUsageService.OrganizationUsageReport>>
            getOrganizationUsage(
                    @RequestParam(required = false) YearMonth month) {

        YearMonth billingMonth = month != null ? month : billingClock.currentMonth();

        return ResponseEntity.ok(
                platformUsageService.getOrganizationUsage(billingMonth)
        );
    }

    // View usage history for one organization.
    @GetMapping("/{organizationId}")
    public ResponseEntity<List<PlatformUsageService.OrganizationUsageReport>>
            getOrganizationHistory(
                    @PathVariable Long organizationId) {

        return ResponseEntity.ok(
                platformUsageService.getOrganizationHistory(organizationId)
        );
    }
}
