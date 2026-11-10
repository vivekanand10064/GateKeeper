package in.vs.gatekeeper.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.vs.gatekeeper.management.entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
