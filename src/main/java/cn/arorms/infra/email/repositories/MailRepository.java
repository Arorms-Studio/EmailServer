package cn.arorms.infra.email.repositories;

import cn.arorms.infra.email.entities.Mail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailRepository extends JpaRepository<Mail, Long> {
}
