package cn.arorms.infra.email.service;

import cn.arorms.infra.email.domain.entity.Mail;
import cn.arorms.infra.email.domain.entity.Mailbox;
import cn.arorms.infra.email.domain.enums.MailboxType;
import cn.arorms.infra.email.domain.repository.MailRepository;
import cn.arorms.infra.email.domain.repository.MailboxRepository;
import cn.arorms.infra.email.domain.repository.UserRepository;
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
     * @param fromUser
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
                .orElseGet(() -> {
                    // Auto-provision a SENT mailbox for users created before
                    // the registration flow provisioned one. Cheap to do
                    // lazily; a unique (owner_id, type) index would be a
                    // future improvement.
                    Mailbox box = new Mailbox();
                    box.setOwner(userRepository.getReferenceById(fromUser.getUserId()));
                    box.setType(MailboxType.SENT);
                    box.setName("Sent");
                    return mailboxRepository.save(box);
                });

        Instant now = Instant.now();
        Mail mail = new Mail(sentBox, messageId, fromUser.getEmailAddress(), subject, now, true, null);
        mail = mailRepository.save(mail);

        return mail;
    }
}