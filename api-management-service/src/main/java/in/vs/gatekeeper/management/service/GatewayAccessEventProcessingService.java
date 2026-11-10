
package in.vs.gatekeeper.management.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.vs.gatekeeper.management.dto.eventDto.GatewayAccessEventMessage;
import in.vs.gatekeeper.management.repository.OrganizationMonthlyUsageRepository;

@Service
public class GatewayAccessEventProcessingService {

    private final JdbcTemplate jdbcTemplate;
    private final OrganizationMonthlyUsageRepository monthlyUsageRepository;

    public GatewayAccessEventProcessingService(
            JdbcTemplate jdbcTemplate,
            OrganizationMonthlyUsageRepository monthlyUsageRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.monthlyUsageRepository = monthlyUsageRepository;
    }

    @Transactional
    public boolean processEvent(GatewayAccessEventMessage event) {

        if (event == null) {
            throw new IllegalArgumentException("Gateway event cannot be null");
        }
        // Keep quota-rejected requests in Analytics,
        // but exclude them from monthly billing usage.
        if (!event.billable()) {
            return false;
        }
        // Unknown routes cannot be assigned to an organization.
        if (event.organizationId() == null || event.organizationId() <= 0) {
            return false;
        }

        // Legacy events without an ID cannot be safely deduplicated.
        if (event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }

        if (event.responseStatus() == null
                || event.responseStatus() < 100
                || event.responseStatus() > 599
                || event.timestamp() == null) {

            throw new IllegalArgumentException(
                    "Invalid Gateway access event");
        }

        LocalDateTime timestamp = event.timestamp();

        // Prefer the month the gateway charged in Redis, so MySQL and Redis
        // always agree on which month a request belongs to.
        LocalDate billingMonth = (event.billingMonth() != null
                && !event.billingMonth().isBlank())
                ? YearMonth.parse(event.billingMonth()).atDay(1)
                : timestamp.toLocalDate().withDayOfMonth(1);

        int inserted = jdbcTemplate.update(
                """
                INSERT IGNORE INTO processed_gateway_access_events
                (event_id)
                VALUES (?)
                """,
                event.eventId()
        );

        if (inserted == 0) {
            return false;
        }

        monthlyUsageRepository.recordRequest(
                event.organizationId(),
                billingMonth,
                event.responseStatus()
        );

        return true;
    }
}
