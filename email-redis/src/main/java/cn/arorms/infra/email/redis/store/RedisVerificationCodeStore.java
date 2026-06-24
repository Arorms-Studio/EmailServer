package cn.arorms.infra.email.redis.store;

import cn.arorms.infra.email.redis.property.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private final RedisProperties redisProps;
    private final StringRedisTemplate redis;

    public RedisVerificationCodeStore(RedisProperties redisProps,
                                      StringRedisTemplate redis) {
        this.redisProps = redisProps;
        this.redis = redis;
    }

    @Override
    public void save(String email, String code, Duration ttl) {
        redis.opsForValue().set(
            redisProps.getKeyPrefix() + "code:email:" + email.toLowerCase(),
            code, ttl);
    }

    @Override
    public boolean consume(String email, String code) {
        String key = redisProps.getKeyPrefix() + "code:email:" + email.toLowerCase();
        String stored = redis.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redis.delete(key);
            return true;
        }
        return false;
    }
}
