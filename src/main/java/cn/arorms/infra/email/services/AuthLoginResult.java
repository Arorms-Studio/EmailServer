package cn.arorms.infra.email.services;

import cn.arorms.infra.email.dtos.TokenResponse;
import org.springframework.http.ResponseCookie;

public record AuthLoginResult(TokenResponse tokenResponse, ResponseCookie refreshCookie) {
}