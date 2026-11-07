package in.vs.gatekeeper.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.vs.gatekeeper.management.entity.ManagementUser;

import java.util.Optional;

public interface ManagementUserRepository extends JpaRepository<ManagementUser, Long> {

    Optional<ManagementUser> findByUsername(String username);

    Optional<ManagementUser> findByOrganizationId(Long organizationId);

    boolean existsByUsername(String username);

}
