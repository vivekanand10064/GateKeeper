package in.vs.gatekeeper.gateway.service;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.gateway.dto.GatewayRoute;

@Service
public class MonthlyQuotaService {

    private static final Logger log =
            LoggerFactory.getLogger(MonthlyQuotaService.class);

    private static final DefaultRedisScript<Long> QUOTA_SCRIPT;

    static {
        QUOTA_SCRIPT = new DefaultRedisScript<>();

        QUOTA_SCRIPT.setScriptText(
            "local current = redis.call('GET', KEYS[1]) " +
            "local databaseUsage = tonumber(ARGV[1]) " +
            "local limit = tonumber(ARGV[2]) " +

            "if not current then " +
            "  current = databaseUsage " +
            "else " +
            "  current = math.max(tonumber(current), databaseUsage) " +
            "end " +

            "if current >= limit then " +
            "  redis.call('SET', KEYS[1], current) " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[3]) " +
            "  return -1 " +
            "end " +

            "redis.call('SET', KEYS[1], current + 1) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[3]) " +

            "return current + 1"
        );

        QUOTA_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final BillingClock billingClock;

    public MonthlyQuotaService(
            StringRedisTemplate redisTemplate,
            BillingClock billingClock) {
        this.redisTemplate = redisTemplate;
        this.billingClock = billingClock;
    }

    public boolean isAllowed(GatewayRoute route) {

        if (route == null || route.getOrganizationId() == null) {
            throw new IllegalArgumentException(
                    "Route organization is required for quota enforcement"
            );
        }

        if (!Boolean.TRUE.equals(route.getQuotaEnabled())) {
            return true;
        }

        Long limit = route.getMonthlyRequestLimit();

        if (limit == null || limit <= 0) {
            throw new IllegalStateException(
                    "Invalid monthly quota for organization "
                            + route.getOrganizationId()
            );
        }

        YearMonth month = billingClock.currentMonth();

        boolean snapshotIsCurrentMonth =
                month.toString().equals(route.getUsageMonth());

        Long initialUsage =
                (!snapshotIsCurrentMonth
                || route.getCurrentMonthRequests() == null)
                ? 0L
                : route.getCurrentMonthRequests();

        if (initialUsage < 0) {
            throw new IllegalStateException("Invalid initial monthly usage");
        }

        String key = "gatekeeper:monthly-quota:"
                + route.getOrganizationId() + ":" + month;

        long secondsUntilNextMonth = Duration.between(
                ZonedDateTime.now(billingClock.zone()),
                month.plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(billingClock.zone())
        ).getSeconds();

        long expirySeconds = Math.max(1, secondsUntilNextMonth);

        Long result = redisTemplate.execute(
                QUOTA_SCRIPT,
                List.of(key),
                String.valueOf(initialUsage),
                String.valueOf(limit),
                String.valueOf(expirySeconds)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Unable to update monthly quota in Redis"
            );
        }

        if (result == -1L) {
            log.warn(
                    "Monthly quota exceeded: organizationId={}, limit={}",
                    route.getOrganizationId(),
                    limit
            );
            return false;
        }

        int threshold =
                route.getWarningThresholdPercent() == null
                ? 80
                : route.getWarningThresholdPercent();

        double usagePercentage = result * 100.0 / limit;

        if (usagePercentage >= threshold) {
            log.warn(
                    "Monthly quota warning: organizationId={}, usage={}%, limit={}",
                    route.getOrganizationId(),
                    String.format("%.2f", usagePercentage),
                    limit
            );
        }

        return true;
    }
}
