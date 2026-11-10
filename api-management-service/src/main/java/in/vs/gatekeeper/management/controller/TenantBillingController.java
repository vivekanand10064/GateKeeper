package in.vs.gatekeeper.management.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.entity.OrganizationInvoice;
import in.vs.gatekeeper.management.repository.OrganizationInvoiceRepository;
import in.vs.gatekeeper.management.service.PlatformUsageService;
import in.vs.gatekeeper.management.service.TenantContext;

/**
 * View-only billing information for the authenticated tenant.
 * Only GET endpoints exist here (and SecurityConfig rejects any other HTTP
 * method under /api/tenant/billing/**).
 */
@RestController
@RequestMapping("/api/tenant/billing")
public class TenantBillingController {

    private final TenantContext tenantContext;
    private final PlatformUsageService usageService;
    private final OrganizationInvoiceRepository invoiceRepository;

    public TenantBillingController(
            TenantContext tenantContext,
            PlatformUsageService usageService,
            OrganizationInvoiceRepository invoiceRepository) {

        this.tenantContext = tenantContext;
        this.usageService = usageService;
        this.invoiceRepository = invoiceRepository;
    }

    // Month-by-month usage and estimated cost for the tenant's organization.
    @GetMapping("/history")
    public ResponseEntity<List<PlatformUsageService.OrganizationUsageReport>>
            getBillingHistory() {

        Organization organization = tenantContext.getOrganization();

        return ResponseEntity.ok(
                usageService.getOrganizationHistory(organization.getId())
        );
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoices() {

        Organization organization = tenantContext.getOrganization();

        List<InvoiceResponse> response = new ArrayList<>();

        for (OrganizationInvoice invoice :
                invoiceRepository
                        .findAllByOrganizationIdOrderByBillingMonthDesc(
                                organization.getId())) {

            response.add(new InvoiceResponse(
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getBillingMonth(),
                    invoice.getAmount(),
                    invoice.getCurrency(),
                    invoice.getStatus(),
                    invoice.getPaymentStatus(),
                    invoice.getIssuedAt()
            ));
        }

        return ResponseEntity.ok(response);
    }

    public record InvoiceResponse(
            Long id,
            String invoiceNumber,
            LocalDate billingMonth,
            BigDecimal amount,
            String currency,
            String status,
            String paymentStatus,
            LocalDateTime issuedAt
    ) {}
}
