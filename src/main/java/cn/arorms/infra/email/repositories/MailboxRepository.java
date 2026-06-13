package cn.arorms.infra.email.repositories;

import cn.arorms.infra.email.entities.Mailbox;
import cn.arorms.infra.email.enums.MailboxType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailboxRepository extends JpaRepository<Mailbox, Long> {
    Optional<Mailbox> findByOwnerIdAndType(Long ownerId, MailboxType mailboxType);
}
