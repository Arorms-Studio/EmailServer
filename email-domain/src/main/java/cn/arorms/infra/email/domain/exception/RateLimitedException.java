package cn.arorms.infra.email.domain.exception;

public class RateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
