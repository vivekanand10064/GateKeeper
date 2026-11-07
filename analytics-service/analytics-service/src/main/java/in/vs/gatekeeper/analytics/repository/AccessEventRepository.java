package in.vs.gatekeeper.analytics.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import in.vs.gatekeeper.analytics.model.AccessEvent;

@Repository
public interface AccessEventRepository
        extends MongoRepository<AccessEvent, String> {

    List<AccessEvent> findAllByOrganizationId(Long organizationId);

    long countByOrganizationId(Long organizationId);

    List<AccessEvent> findAllByOrganizationIdAndTimestampBetween(
            Long organizationId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}