package cn.arorms.infra.email.redis.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    /**
     * Acquires a one-shot cooldown slot for `email`. Returns null on
     * success; otherwise returns a hint of how many seconds remain
     * on the existing cooldown.
     */
    Long acquireEmailCooldown(String email, Duration cooldown);

    /**
     * Acquires one slot in the rolling quota for `ip`. Returns null
     * while count <= quota; otherwise returns the remaining TTL of
     * the quota window in seconds.
     */
    Long acquireIpQuota(String ip, int quota, Duration window);
}
