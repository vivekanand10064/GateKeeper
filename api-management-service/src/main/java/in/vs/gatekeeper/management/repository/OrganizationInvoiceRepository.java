package in.vs.gatekeeper.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import in.vs.gatekeeper.management.entity.OrganizationInvoice;

@Repository
public interface OrganizationInvoiceRepository
        extends JpaRepository<OrganizationInvoice, Long> {

    List<OrganizationInvoice> findAllByOrganizationIdOrderByBillingMonthDesc(
            Long organizationId
    );
}
