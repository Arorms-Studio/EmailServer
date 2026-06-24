package cn.arorms.infra.email.redis.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "application.verification")
public class RateLimitProperties {
    private Duration sendCodeCooldownPerEmail = Duration.ofMinutes(1);
    private int sendCodeQuotaPerIp = 5;
    private Duration sendCodeQuotaWindow = Duration.ofMinutes(1);

    public Duration getSendCodeCooldownPerEmail() { return sendCodeCooldownPerEmail; }
    public void setSendCodeCooldownPerEmail(Duration d) { this.sendCodeCooldownPerEmail = d; }

    public int getSendCodeQuotaPerIp() { return sendCodeQuotaPerIp; }
    public void setSendCodeQuotaPerIp(int n) { this.sendCodeQuotaPerIp = n; }

    public Duration getSendCodeQuotaWindow() { return sendCodeQuotaWindow; }
    public void setSendCodeQuotaWindow(Duration d) { this.sendCodeQuotaWindow = d; }
}
