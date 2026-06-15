package cn.arorms.infra.email.security;

import cn.arorms.infra.email.domain.property.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues and parses both access and refresh JWTs (HS256).
 *
 * <p>Decoding is delegated to the {@code JwtDecoder} bean configured in
 * {@code SecurityConfig} so the resource-server filter chain and this service
 * share the same validators (issuer, audience, expiry).
 */
@Service
public class JwtService {

    public static final String CLAIM_TOKEN_TYPE     = "tokenType";
    public static final String CLAIM_USERNAME       = "username";
    public static final String CLAIM_EMAIL_ADDRESS  = "emailAddress";

    public static final String TOKEN_TYPE_ACCESS  = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties props;
    private final JwtEncoder encoder;
    private final org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    public JwtService(JwtProperties props,
                      org.springframework.security.oauth2.jwt.JwtDecoder decoder) {
        this.props = props;
        this.decoder = decoder;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(props.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }

    public String issueAccess(String username, String emailAddress) {
        return mint(username, emailAddress, TOKEN_TYPE_ACCESS, props.getAccessTtl().toSeconds());
    }

    public String issueRefresh(String username, String emailAddress) {
        return mint(username, emailAddress, TOKEN_TYPE_REFRESH, props.getRefreshTtl().toSeconds());
    }

    public Jwt parse(String token) {
        return decoder.decode(token);   // throws JwtException on any failure (sig, exp, iss, aud)
    }

    public Jwt parseAccess(String token) {
        Jwt jwt = parse(token);
        requireTokenType(jwt, TOKEN_TYPE_ACCESS);
        return jwt;
    }

    public Jwt parseRefresh(String token) {
        Jwt jwt = parse(token);
        requireTokenType(jwt, TOKEN_TYPE_REFRESH);
        return jwt;
    }

    private String mint(String username, String emailAddress, String tokenType, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getIssuer())
                .audience(List.of(props.getAudience()))
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_EMAIL_ADDRESS, emailAddress)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static void requireTokenType(Jwt jwt, String expected) {
        Object actual = jwt.getClaim(CLAIM_TOKEN_TYPE);
        if (!expected.equals(actual)) {
            throw new JwtException("Invalid token type: expected " + expected + " but was " + actual);
        }
    }
}