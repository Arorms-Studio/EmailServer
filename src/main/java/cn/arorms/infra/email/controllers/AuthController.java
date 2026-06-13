package cn.arorms.infra.email.controllers;

import cn.arorms.infra.email.controllers.dto.LoginRequest;
import cn.arorms.infra.email.controllers.dto.TokenResponse;
import cn.arorms.infra.email.controllers.dto.UserInfoResponse;
import cn.arorms.infra.email.services.AuthLoginResult;
import cn.arorms.infra.email.services.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public UserInfoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt);
    }
}