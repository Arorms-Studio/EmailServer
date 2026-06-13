package cn.arorms.infra.email.controllers;

import cn.arorms.infra.email.entities.Mail;
import cn.arorms.infra.email.security.AppUserPrincipal;
import cn.arorms.infra.email.services.MailService;
import jakarta.mail.MessagingException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
public class MailController {
    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    public record SendRequest(String to, String subject, String content) {}

    @PostMapping("/send")
    public Mail send(@AuthenticationPrincipal AppUserPrincipal user, @RequestBody SendRequest request) throws MessagingException {
        return mailService.send(user, request.to(), request.subject(), request.content());
    }
}
