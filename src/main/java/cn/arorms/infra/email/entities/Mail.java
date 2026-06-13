package cn.arorms.infra.email.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Entity
@Table(name = "mails")
public class Mail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private Mailbox mailbox;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "from_address", length = 255)
    private String fromAddress;

    @Column(length = 998)
    private String subject;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private boolean seen = false;

    @Column(name = "raw_path", length = 512)
    private String rawPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Mail(Mailbox mailbox, String messageId, String fromAddress, String subject, Instant sentAt, boolean seen, String rawPath) {
        this.mailbox = mailbox;
        this.messageId = messageId;
        this.fromAddress = fromAddress;
        this.subject = subject;
        this.sentAt = sentAt;
        this.seen = seen;
        this.rawPath = rawPath;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Mailbox getMailbox() {
        return mailbox;
    }

    public void setMailbox(Mailbox mailbox) {
        this.mailbox = mailbox;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}