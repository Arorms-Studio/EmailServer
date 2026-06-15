package cn.arorms.infra.email.domain.repository;

import cn.arorms.infra.email.domain.entity.Mailbox;
import cn.arorms.infra.email.domain.enums.MailboxType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailboxRepository extends JpaRepository<Mailbox, Long> {
    Optional<Mailbox> findByOwnerIdAndType(Long ownerId, MailboxType mailboxType);
}
