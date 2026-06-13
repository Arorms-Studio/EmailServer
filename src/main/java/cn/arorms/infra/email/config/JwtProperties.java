package cn.arorms.infra.email.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "application.jwt")
public class JwtProperties {

    private String secret;
    private String issuer;
    private String audience;
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(7);

    private String refreshCookieName = "REFRESH_TOKEN";
    private String refreshCookiePath = "/api/auth/refresh";
    private boolean refreshCookieSecure = true;
    private String refreshCookieSameSite = "Strict";
    private int refreshCookieMaxAge = 604_800;

    @PostConstruct
    void validate() {
        Assert.hasText(secret, "application.jwt.secret must not be blank");
        Assert.isTrue(secret.getBytes(StandardCharsets.UTF_8).length >= 32,
                "application.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        Assert.hasText(issuer, "application.jwt.issuer must not be blank");
        Assert.hasText(audience, "application.jwt.audience must not be blank");
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public Duration getAccessTtl() { return accessTtl; }
    public void setAccessTtl(Duration accessTtl) { this.accessTtl = accessTtl; }

    public Duration getRefreshTtl() { return refreshTtl; }
    public void setRefreshTtl(Duration refreshTtl) { this.refreshTtl = refreshTtl; }

    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }

    public String getRefreshCookiePath() { return refreshCookiePath; }
    public void setRefreshCookiePath(String refreshCookiePath) { this.refreshCookiePath = refreshCookiePath; }

    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }

    public String getRefreshCookieSameSite() { return refreshCookieSameSite; }
    public void setRefreshCookieSameSite(String refreshCookieSameSite) { this.refreshCookieSameSite = refreshCookieSameSite; }

    public int getRefreshCookieMaxAge() { return refreshCookieMaxAge; }
    public void setRefreshCookieMaxAge(int refreshCookieMaxAge) { this.refreshCookieMaxAge = refreshCookieMaxAge; }
}