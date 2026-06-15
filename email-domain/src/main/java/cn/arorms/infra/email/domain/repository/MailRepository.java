package cn.arorms.infra.email.domain.repository;

import cn.arorms.infra.email.domain.entity.Mail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailRepository extends JpaRepository<Mail, Long> {
}
