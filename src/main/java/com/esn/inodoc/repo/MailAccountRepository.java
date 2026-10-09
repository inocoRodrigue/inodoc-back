package com.esn.inodoc.repo;

import com.esn.inodoc.domain.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailAccountRepository extends JpaRepository<MailAccount, UUID> {

    List<MailAccount> findAllByOrderByCreatedAtAsc();

    Optional<MailAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<MailAccount> findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(UUID userId);

    Optional<MailAccount> findByCreatedByAndActiveTrue(UUID userId);
}
