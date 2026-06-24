package cn.arorms.infra.email.domain.exception;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("Verification code invalid or expired");
    }
}
