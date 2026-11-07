package in.vs.gatekeeper.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.vs.gatekeeper.management.entity.ControlPlaneAuditRecord;

import java.util.List;

public interface ControlPlaneAuditRecordRepository extends JpaRepository<ControlPlaneAuditRecord, Long> {

    List<ControlPlaneAuditRecord> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
