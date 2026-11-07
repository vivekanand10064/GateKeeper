package in.vs.gatekeeper.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.vs.gatekeeper.management.entity.RouteConfigurationVersion;

import java.util.List;

public interface RouteConfigurationVersionRepository extends JpaRepository<RouteConfigurationVersion, Long> {

    List<RouteConfigurationVersion> findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(Long organizationId,
                                                                                              String routeId);

}
