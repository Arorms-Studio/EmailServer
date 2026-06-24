package cn.arorms.infra.email.redis.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.redis")
public class RedisProperties {
    /** Prefix prepended to every key this application writes to Redis. */
    private String keyPrefix = "email-server:";

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
}
