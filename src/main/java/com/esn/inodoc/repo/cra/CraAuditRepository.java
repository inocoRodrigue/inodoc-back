package com.esn.inodoc.repo.cra;
import com.esn.inodoc.domain.cra.CraAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
public interface CraAuditRepository extends JpaRepository<CraAuditEvent, UUID> {
    Page<CraAuditEvent> findByDeclarationIdOrderByCreatedAtDesc(UUID declarationId, Pageable pageable);
}