package cn.arorms.infra.email.domain.dto;

public record RegisterRequest(String username, String password,
                              String email, String code) {
}
