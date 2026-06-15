package cn.arorms.infra.email.service;

import cn.arorms.infra.email.domain.dto.LoginRequest;
import cn.arorms.infra.email.domain.dto.RegisterRequest;
import cn.arorms.infra.email.domain.dto.TokenResponse;
import cn.arorms.infra.email.domain.dto.UserInfoResponse;
import cn.arorms.infra.email.domain.entity.User;
import cn.arorms.infra.email.domain.exception.DuplicateUsernameException;
import cn.arorms.infra.email.domain.exception.InvalidRefreshTokenException;
import cn.arorms.infra.email.domain.property.JwtProperties;
import cn.arorms.infra.email.domain.repository.UserRepository;
import cn.arorms.infra.email.security.AppUserPrincipal;
import cn.arorms.infra.email.security.JwtService;
import cn.arorms.infra.email.security.RefreshCookieFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       RefreshCookieFactory cookieFactory,
                       JwtProperties props,
                       PasswordEncoder passwordEncoder) {
        this.authManager = authManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
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

    public AuthLoginResult register(RegisterRequest req) {
        if (req == null
                || !StringUtils.hasText(req.username())
                || req.username().length() > 64) {
            throw new IllegalArgumentException("Username must be 1-64 characters");
        }
        if (!StringUtils.hasText(req.password())
                || req.password().length() < 8
                || req.password().length() > 72) {
            throw new IllegalArgumentException("Password must be 8-72 characters");
        }
        String username = req.username();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.password()));
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException(username);
        }
        return mintTokens(user.getUsername(), user.getEmailAddress());
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

    AuthLoginResult mintTokens(String username, String emailAddress) {
        String access = jwtService.issueAccess(username, emailAddress);
        String refresh = jwtService.issueRefresh(username, emailAddress);
        TokenResponse tokenResponse = TokenResponse.bearer(access, props.getAccessTtl().toSeconds());
        return new AuthLoginResult(tokenResponse, cookieFactory.create(refresh));
    }
}