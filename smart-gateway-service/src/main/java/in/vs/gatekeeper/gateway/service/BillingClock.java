package in.vs.gatekeeper.gateway.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "which billing month is it". The gateway (Redis
 * counter) and the management service (MySQL counter) MUST use the same zone,
 * otherwise they disagree on the month around the 1st of every month.
 */
@Component
public class BillingClock {

    private final ZoneId zone;

    public BillingClock(@Value("${gatekeeper.billing.zone:UTC}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public ZoneId zone() {
        return zone;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(zone);
    }

    public YearMonth monthOf(long epochMillis) {
        return YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
