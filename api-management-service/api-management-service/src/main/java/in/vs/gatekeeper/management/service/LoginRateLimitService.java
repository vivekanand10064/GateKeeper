
package in.vs.gatekeeper.management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimitService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION =
            Duration.ofMinutes(15);

    private static final String KEY_PREFIX =
            "gatekeeper:login-failures:";

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlocked(String username, String clientIp) {

        String key = buildKey(username, clientIp);

        String attempts = redisTemplate.opsForValue().get(key);

        return attempts != null
                && Integer.parseInt(attempts) >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailedAttempt(String username, String clientIp) {

        String key = buildKey(username, clientIp);

        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
    }

    public void resetFailedAttempts(String username, String clientIp) {

        redisTemplate.delete(buildKey(username, clientIp));
    }

    private String buildKey(String username, String clientIp) {

        String identity = clientIp + ":" +
                username.trim().toLowerCase();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            String hash = HexFormat.of().formatHex(
                    digest.digest(identity.getBytes(StandardCharsets.UTF_8))
            );

            return KEY_PREFIX + hash;

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to create login rate-limit key", exception
            );
        }
    }
}
