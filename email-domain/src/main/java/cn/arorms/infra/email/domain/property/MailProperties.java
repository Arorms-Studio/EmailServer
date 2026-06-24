package cn.arorms.infra.email.domain.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.mail")
public class MailProperties {
    private String domain = "arorms.cn";
    private String defaultFrom = "noreply";

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDefaultFrom() { return defaultFrom; }
    public void setDefaultFrom(String defaultFrom) { this.defaultFrom = defaultFrom; }
}
