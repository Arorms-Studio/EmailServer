package cn.arorms.infra.email.redis.ratelimit;

import cn.arorms.infra.email.redis.property.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisRateLimiter implements RateLimiter {

    private final RedisProperties redisProps;
    private final StringRedisTemplate redis;

    public RedisRateLimiter(RedisProperties redisProps,
                            StringRedisTemplate redis) {
        this.redisProps = redisProps;
        this.redis = redis;
    }

    @Override
    public Long acquireEmailCooldown(String email, Duration cooldown) {
        String key = redisProps.getKeyPrefix()
            + "rate:email:send-code:" + email.toLowerCase();
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", cooldown);
        return Boolean.TRUE.equals(ok) ? null : cooldown.getSeconds();
    }

    @Override
    public Long acquireIpQuota(String ip, int quota, Duration window) {
        String key = redisProps.getKeyPrefix() + "rate:ip:send-code:" + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        if (count != null && count > quota) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            return (ttl == null || ttl < 0) ? window.getSeconds() : ttl;
        }
        return null;
    }
}
