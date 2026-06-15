package cn.arorms.infra.email.service;

import cn.arorms.infra.email.domain.dto.TokenResponse;
import org.springframework.http.ResponseCookie;

public record AuthLoginResult(TokenResponse tokenResponse, ResponseCookie refreshCookie) {
}