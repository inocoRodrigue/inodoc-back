package com.esn.inodoc.repo;

import com.esn.inodoc.domain.SharedLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SharedLinkRepository extends JpaRepository<SharedLink, UUID> {

    /**
     * Find a shared link by token, eagerly loading the associated dossier.
     * Prevents N+1 queries when accessing link.getDossier().
     */
    @Query("SELECT sl FROM SharedLink sl " +
           "JOIN FETCH sl.dossier " +
           "WHERE sl.token = :token")
    Optional<SharedLink> findByTokenWithDossier(@Param("token") String token);

    /**
     * Find a shared link by token (basic query).
     * @deprecated Use findByTokenWithDossier() to avoid N+1 queries
     */
    @Deprecated
    Optional<SharedLink> findByToken(String token);

    Page<SharedLink> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
