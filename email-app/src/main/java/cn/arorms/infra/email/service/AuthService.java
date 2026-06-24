package cn.arorms.infra.email.service;

import cn.arorms.infra.email.domain.dto.LoginRequest;
import cn.arorms.infra.email.domain.dto.RegisterRequest;
import cn.arorms.infra.email.domain.dto.TokenResponse;
import cn.arorms.infra.email.domain.dto.UserInfoResponse;
import cn.arorms.infra.email.domain.entity.User;
import cn.arorms.infra.email.domain.exception.DuplicateEmailException;
import cn.arorms.infra.email.domain.exception.DuplicateUsernameException;
import cn.arorms.infra.email.domain.exception.InvalidEmailFormatException;
import cn.arorms.infra.email.domain.exception.InvalidRefreshTokenException;
import cn.arorms.infra.email.domain.exception.InvalidVerificationCodeException;
import cn.arorms.infra.email.domain.exception.RateLimitedException;
import cn.arorms.infra.email.domain.property.JwtProperties;
import cn.arorms.infra.email.domain.repository.UserRepository;
import cn.arorms.infra.email.redis.property.RateLimitProperties;
import cn.arorms.infra.email.redis.property.VerificationProperties;
import cn.arorms.infra.email.redis.ratelimit.RateLimiter;
import cn.arorms.infra.email.redis.store.VerificationCodeStore;
import cn.arorms.infra.email.security.AppUserPrincipal;
import cn.arorms.infra.email.security.JwtService;
import cn.arorms.infra.email.security.RefreshCookieFactory;
import jakarta.mail.MessagingException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshCookieFactory cookieFactory;
    private final JwtProperties props;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeStore codeStore;
    private final RateLimiter rateLimiter;
    private final VerificationProperties verificationProps;
    private final RateLimitProperties rateLimitProps;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();
    private static final Pattern EMAIL_RE =
        Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CODE_RE = Pattern.compile("\\d{6}");

    public AuthService(AuthenticationManager authManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       RefreshCookieFactory cookieFactory,
                       JwtProperties props,
                       PasswordEncoder passwordEncoder,
                       VerificationCodeStore codeStore,
                       RateLimiter rateLimiter,
                       VerificationProperties verificationProps,
                       RateLimitProperties rateLimitProps,
                       MailService mailService) {
        this.authManager = authManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.cookieFactory = cookieFactory;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.codeStore = codeStore;
        this.rateLimiter = rateLimiter;
        this.verificationProps = verificationProps;
        this.rateLimitProps = rateLimitProps;
        this.mailService = mailService;
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

    public void sendCode(String email, String clientIp) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new InvalidEmailFormatException();
        }
        String lower = email.toLowerCase();

        Long cooldown = rateLimiter.acquireEmailCooldown(
            lower, rateLimitProps.getSendCodeCooldownPerEmail());
        if (cooldown != null) {
            throw new RateLimitedException(cooldown,
                "Code already sent, retry in " + cooldown + "s");
        }
        Long quota = rateLimiter.acquireIpQuota(
            clientIp,
            rateLimitProps.getSendCodeQuotaPerIp(),
            rateLimitProps.getSendCodeQuotaWindow());
        if (quota != null) {
            throw new RateLimitedException(quota,
                "Too many requests from this IP");
        }

        String code = generateCode(verificationProps.getCodeLength());
        codeStore.save(lower, code, verificationProps.getCodeTtl());
        try {
            mailService.sendVerificationCode(lower, code);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
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
        if (req.email() == null || req.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (!EMAIL_RE.matcher(req.email()).matches()) {
            throw new InvalidEmailFormatException();
        }
        if (req.code() == null || !CODE_RE.matcher(req.code()).matches()) {
            throw new IllegalArgumentException("Invalid code format");
        }
        String username = req.username();
        String lowerEmail = req.email().toLowerCase();

        if (!codeStore.consume(lowerEmail, req.code())) {
            throw new InvalidVerificationCodeException();
        }
        if (userRepository.findByRegistrationEmail(lowerEmail).isPresent()) {
            throw new DuplicateEmailException(lowerEmail);
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setRegistrationEmail(lowerEmail);
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
