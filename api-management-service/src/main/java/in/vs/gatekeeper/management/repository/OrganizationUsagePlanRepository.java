
package in.vs.gatekeeper.management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import in.vs.gatekeeper.management.entity.OrganizationUsagePlan;

@Repository
public interface OrganizationUsagePlanRepository
        extends JpaRepository<OrganizationUsagePlan, Long> {

    Optional<OrganizationUsagePlan> findByOrganizationId(Long organizationId);

    boolean existsByOrganizationId(Long organizationId);
}
