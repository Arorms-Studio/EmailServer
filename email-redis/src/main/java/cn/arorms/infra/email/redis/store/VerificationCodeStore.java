package cn.arorms.infra.email.redis.store;

import java.time.Duration;

public interface VerificationCodeStore {
    /** Persist `code` for `email` with the given TTL. */
    void save(String email, String code, Duration ttl);

    /**
     * Returns true if `code` matches the stored value and consumes it.
     * Returns false on miss, mismatch, or already-consumed.
     */
    boolean consume(String email, String code);
}
