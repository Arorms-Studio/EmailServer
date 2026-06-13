package cn.arorms.infra.email.security;

import cn.arorms.infra.email.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {

    private final JwtProperties props;

    public RefreshCookieFactory(JwtProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String token) {
        return base(token, Duration.ofSeconds(props.getRefreshCookieMaxAge())).build();
    }

    public ResponseCookie clear() {
        return base("", Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value, Duration maxAge) {
        return ResponseCookie.from(props.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(props.isRefreshCookieSecure())
                .sameSite(props.getRefreshCookieSameSite())
                .path(props.getRefreshCookiePath())
                .maxAge(maxAge);
    }
}