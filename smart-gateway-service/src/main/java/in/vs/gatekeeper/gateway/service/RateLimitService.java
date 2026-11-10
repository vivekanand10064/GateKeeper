
package in.vs.gatekeeper.gateway.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

        RATE_LIMIT_SCRIPT.setScriptText(
                "local count = redis.call('INCR', KEYS[1]) " +
                "if count == 1 then " +
                "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "return count"
        );

        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(
            Long organizationId,
            String routeId,
            Integer requestsPerMinute) {

        if (requestsPerMinute == null || requestsPerMinute <= 0) {
            return true;
        }

        String key = "gatekeeper:rate-limit:"
                + organizationId + ":" + routeId;

        Long requestCount = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(WINDOW.toSeconds())
        );

        if (requestCount == null) {
            throw new IllegalStateException(
                    "Unable to update Redis rate-limit counter"
            );
        }

        return requestCount <= requestsPerMinute;
    }
}
