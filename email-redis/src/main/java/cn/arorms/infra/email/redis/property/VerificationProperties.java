package cn.arorms.infra.email.redis.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "application.verification")
public class VerificationProperties {
    private Duration codeTtl = Duration.ofMinutes(5);
    private int codeLength = 6;

    public Duration getCodeTtl() { return codeTtl; }
    public void setCodeTtl(Duration codeTtl) { this.codeTtl = codeTtl; }

    public int getCodeLength() { return codeLength; }
    public void setCodeLength(int codeLength) { this.codeLength = codeLength; }
}
