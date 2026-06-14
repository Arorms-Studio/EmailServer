package cn.arorms.infra.email.security;

import cn.arorms.infra.email.entities.User;
import cn.arorms.infra.email.repositories.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Replaces Spring Security's default {@code JwtAuthenticationToken} (whose
 * principal is the raw {@link Jwt}) with one whose principal is the
 * {@link AppUserPrincipal} loaded from the database.
 *
 * <p>This is what lets controllers write
 * {@code @AuthenticationPrincipal AppUserPrincipal user} and get a real,
 * non-null value. The DB lookup runs once per authenticated request — a
 * single indexed {@code findByUsername} — which is acceptable for now.
 * If/when the access-token issuer ({@link JwtService}) starts putting
 * {@code userId} into JWT claims, this lookup can be removed and the
 * principal can be built directly from the claims.
 */
@Component
public class JwtToAppUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public JwtToAppUserAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getSubject();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        // The credentials argument is informational only — Spring's
        // UsernamePasswordAuthenticationToken keeps it around but never
        // re-validates it during a JWT-authenticated request.
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }
}
