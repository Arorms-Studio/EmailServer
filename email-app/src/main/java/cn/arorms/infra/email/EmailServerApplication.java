package cn.arorms.infra.email;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "cn.arorms.infra")
@ConfigurationPropertiesScan(basePackages = "cn.arorms.infra")
@EntityScan(basePackages = "cn.arorms.infra.email.domain.entity")
@EnableJpaRepositories(basePackages = "cn.arorms.infra.email.domain.repository")
public class EmailServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailServerApplication.class, args);
    }
}