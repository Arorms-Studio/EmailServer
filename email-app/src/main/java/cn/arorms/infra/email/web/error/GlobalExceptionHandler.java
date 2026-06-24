package cn.arorms.infra.email.web.error;

import cn.arorms.infra.email.domain.exception.DuplicateEmailException;
import cn.arorms.infra.email.domain.exception.DuplicateUsernameException;
import cn.arorms.infra.email.domain.exception.ErrorBody;
import cn.arorms.infra.email.domain.exception.InvalidEmailFormatException;
import cn.arorms.infra.email.domain.exception.InvalidRefreshTokenException;
import cn.arorms.infra.email.domain.exception.InvalidVerificationCodeException;
import cn.arorms.infra.email.domain.exception.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String WWW_AUTHENTICATE_VALUE = "Bearer error=\"invalid_token\"";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorBody.of("bad_request", "Malformed JSON", req.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorBody.of("bad_request", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorBody> conflict(DuplicateUsernameException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorBody.of("username_taken", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidEmailFormatException.class)
    public ResponseEntity<ErrorBody> badEmail(InvalidEmailFormatException ex,
                                              HttpServletRequest req) {
        return ResponseEntity.badRequest()
            .body(ErrorBody.of("bad_request", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ErrorBody> badCode(InvalidVerificationCodeException ex,
                                              HttpServletRequest req) {
        return ResponseEntity.badRequest()
            .body(ErrorBody.of("invalid_code", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorBody> emailTaken(DuplicateEmailException ex,
                                                HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorBody.of("email_taken", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorBody> rateLimited(RateLimitedException ex,
                                                 HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .body(ErrorBody.of("rate_limited", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorBody> missingCookie(MissingRequestCookieException ex, HttpServletRequest req) {
        return ResponseEntity.status(401)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE)
                .body(ErrorBody.of("unauthorized", "Refresh token missing", req.getRequestURI()));
    }

    @ExceptionHandler({
            BadCredentialsException.class,
            UsernameNotFoundException.class,
            DisabledException.class,
            InvalidRefreshTokenException.class,
            JwtException.class
    })
    public ResponseEntity<ErrorBody> auth(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(401)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE)
                .body(ErrorBody.of("unauthorized", "Invalid or expired credentials", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> denied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(403)
                .body(ErrorBody.of("forbidden", "Access denied", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception ex, HttpServletRequest req) {
        log.error("unhandled error on {}", req.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorBody.of("internal", "Internal server error", req.getRequestURI()));
    }
}
