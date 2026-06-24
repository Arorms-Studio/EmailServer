package cn.arorms.infra.email.web;

import cn.arorms.infra.email.domain.dto.LoginRequest;
import cn.arorms.infra.email.domain.dto.RegisterRequest;
import cn.arorms.infra.email.domain.dto.TokenResponse;
import cn.arorms.infra.email.domain.dto.UserInfoResponse;
import cn.arorms.infra.email.service.AuthLoginResult;
import cn.arorms.infra.email.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<Void> sendCode(
            @RequestBody SendCodeRequest req,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        authService.sendCode(req.email(), ip);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest req) {
        AuthLoginResult result = authService.register(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.tokenResponse());
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest req) {
        AuthLoginResult result = authService.login(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.tokenResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(value = "${application.jwt.refresh-cookie-name}", required = false) String refreshToken) {
        AuthLoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.tokenResponse());
    }

    @GetMapping("/me")
    public UserInfoResponse me(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getCredentials();
        return authService.me(jwt);
    }
}
