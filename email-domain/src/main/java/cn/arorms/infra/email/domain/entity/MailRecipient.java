package cn.arorms.infra.email.domain.entity;

import cn.arorms.infra.email.domain.enums.DeliveryStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "mail_recipients")
public class MailRecipient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mail_id", nullable = false)
    private Mail mail;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}