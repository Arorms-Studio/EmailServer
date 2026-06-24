package cn.arorms.infra.email.redis.config;

import cn.arorms.infra.email.redis.property.RateLimitProperties;
import cn.arorms.infra.email.redis.property.RedisProperties;
import cn.arorms.infra.email.redis.property.VerificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties({
    RedisProperties.class,
    VerificationProperties.class,
    RateLimitProperties.class
})
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }
}
