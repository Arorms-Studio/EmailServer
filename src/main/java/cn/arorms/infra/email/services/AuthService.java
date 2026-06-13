package cn.arorms.infra.email.services;

import cn.arorms.infra.email.config.JwtProperties;
import cn.arorms.infra.email.controllers.dto.LoginRequest;
import cn.arorms.infra.email.controllers.dto.TokenResponse;
import cn.arorms.infra.email.controllers.dto.UserInfoResponse;
import cn.arorms.infra.email.exception.InvalidRefreshTokenException;
import cn.arorms.infra.email.repositories.UserRepository;
import cn.arorms.infra.email.security.AppUserPrincipal;
import cn.arorms.infra.email.security.JwtService;
import cn.arorms.infra.email.security.RefreshCookieFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshCookieFactory cookieFactory;
    private final JwtProperties props;

    public AuthService(AuthenticationManager authManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       RefreshCookieFactory cookieFactory,
                       JwtProperties props) {
        this.authManager = authManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
        this.props = props;
    }

    public AuthLoginResult login(LoginRequest req) {
        if (req == null || !StringUtils.hasText(req.username()) || !StringUtils.hasText(req.password())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        return mintTokens(principal.getUsername(), principal.getEmailAddress());
    }

    public AuthLoginResult refresh(String refreshTokenValue) {
        if (!StringUtils.hasText(refreshTokenValue)) {
            throw new InvalidRefreshTokenException("Refresh token missing");
        }
        Jwt jwt = jwtService.parseRefresh(refreshTokenValue);   // throws JwtException on bad/expired/wrong-type
        String username = jwt.getSubject();
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidRefreshTokenException("User not found"));
        if (!user.isEnabled()) {
            throw new InvalidRefreshTokenException("User disabled");
        }
        return mintTokens(user.getUsername(), user.getEmailAddress());
    }

    public UserInfoResponse me(Jwt accessJwt) {
        String username = accessJwt.getSubject();
        String emailAddress = accessJwt.getClaimAsString(JwtService.CLAIM_EMAIL_ADDRESS);
        return new UserInfoResponse(username, emailAddress);
    }

    private AuthLoginResult mintTokens(String username, String emailAddress) {
        String access = jwtService.issueAccess(username, emailAddress);
        String refresh = jwtService.issueRefresh(username, emailAddress);
        TokenResponse tokenResponse = TokenResponse.bearer(access, props.getAccessTtl().toSeconds());
        return new AuthLoginResult(tokenResponse, cookieFactory.create(refresh));
    }
}