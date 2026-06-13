package cn.arorms.infra.email.services;

import cn.arorms.infra.email.entities.Mail;
import cn.arorms.infra.email.enums.MailboxType;
import cn.arorms.infra.email.repositories.MailRepository;
import cn.arorms.infra.email.repositories.MailboxRepository;
import cn.arorms.infra.email.repositories.UserRepository;
import cn.arorms.infra.email.security.AppUserPrincipal;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MailService {

    private final JavaMailSenderImpl mailSender;
    private final MailboxRepository mailboxRepository;
    private final UserRepository userRepository;
    private final MailRepository mailRepository;

    public MailService(JavaMailSenderImpl mailSender, MailboxRepository mailboxRepository, UserRepository userRepository, MailRepository mailRepository) {
        this.mailSender = mailSender;
        this.mailboxRepository = mailboxRepository;
        this.userRepository = userRepository;
        this.mailRepository = mailRepository;
    }

    /**
     *
     * @param fromUser 已认证用户(由 SecurityFilterChain 注入,不依赖 ORM 状态)
     * @param to email address
     * @param subject
     * @param content
     * @return
     * @throws MessagingException
     */
    @Transactional
    public Mail send(AppUserPrincipal fromUser, String to, String subject, String content) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromUser.getEmailAddress());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content);
        String messageId = "<" + UUID.randomUUID() + "@arorms.cn" + ">";
        helper.getMimeMessage().setHeader("Message-ID", messageId);
        mailSender.send(message);

        var sentBox = mailboxRepository
                .findByOwnerIdAndType(fromUser.getUserId(), MailboxType.SENT)
                .orElseThrow(() -> new IllegalStateException("SENT mailbox not found for user " + fromUser.getUserId()));

        Instant now = Instant.now();
        Mail mail = new Mail(sentBox, messageId, fromUser.getEmailAddress(), subject, now, true, null);
        mail = mailRepository.save(mail);

        return mail;
    }
}