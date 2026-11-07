package in.vs.gatekeeper.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.vs.gatekeeper.management.entity.Organization;
import in.vs.gatekeeper.management.entity.OrganizationStatus;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlugIgnoreCase(String slug);

    List<Organization> findAllByStatusNot(OrganizationStatus status);

    Optional<Organization> findByIdAndStatusNot(Long id, OrganizationStatus status);
}
