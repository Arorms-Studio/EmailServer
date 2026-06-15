package cn.arorms.infra.email.domain.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public static ErrorBody of(String error, String message, String path) {
        return new ErrorBody(error, message, path, Instant.now());
    }
}