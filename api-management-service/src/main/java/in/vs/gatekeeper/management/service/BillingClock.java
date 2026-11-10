package in.vs.gatekeeper.management.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Must use the same zone as the gateway's BillingClock, otherwise the
 * "current month" differs between Redis and MySQL around month boundaries.
 */
@Component
public class BillingClock {

    private final ZoneId zone;

    public BillingClock(@Value("${gatekeeper.billing.zone:UTC}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public YearMonth currentMonth() {
        return YearMonth.now(zone);
    }

    public LocalDate currentBillingMonth() {
        return currentMonth().atDay(1);
    }
}
