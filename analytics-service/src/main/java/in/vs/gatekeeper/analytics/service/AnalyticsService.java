package in.vs.gatekeeper.analytics.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import in.vs.gatekeeper.analytics.model.AccessEvent;
import in.vs.gatekeeper.analytics.repository.AccessEventRepository;
import in.vs.gatekeeper.analytics.security.TenantContext;

@Service
public class AnalyticsService {

    private final AccessEventRepository eventRepository;
    private final TenantContext tenantContext;

    public AnalyticsService(
            AccessEventRepository eventRepository,
            TenantContext tenantContext) {
        this.eventRepository = eventRepository;
        this.tenantContext = tenantContext;
    }

    public AccessEvent saveEvent(AccessEvent event) {
        return eventRepository.save(event);
    }

    public List<AccessEvent> getAllEvents() {
        Long organizationId = tenantContext.getCurrentOrganizationId();
        return eventRepository.findAllByOrganizationId(organizationId);
    }

    public List<AccessEvent> getEventsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date.");
        }

        Long organizationId = tenantContext.getCurrentOrganizationId();

        return eventRepository
                .findAllByOrganizationIdAndTimestampBetween(
                        organizationId,
                        startDate,
                        endDate
                );
    }

    public long getTotalEvents() {
        Long organizationId = tenantContext.getCurrentOrganizationId();
        return eventRepository.countByOrganizationId(organizationId);
    }
}