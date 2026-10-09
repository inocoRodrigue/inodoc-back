package com.esn.inodoc.repo;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class UserDataCleanupRepository {
    private final EntityManager entityManager;

    public UserDataCleanupRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void deleteAssociatedData(UUID userId, UUID profileId) {
        entityManager.createQuery("delete from CareerEntry e where e.userId = :id")
            .setParameter("id", userId).executeUpdate();
        entityManager.createQuery("delete from ReviewSubmission s where s.userId = :id")
            .setParameter("id", userId).executeUpdate();
        entityManager.createQuery("delete from RefreshToken t where t.user.id = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createQuery("delete from MailAccount m where m.createdBy = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createQuery("delete from SharedLink s where s.createdBy = :id")
                .setParameter("id", userId).executeUpdate();
        if (profileId != null) {
            entityManager.createQuery("delete from SharedLink s where s.dossier.id in "
                            + "(select d.id from CompetenceDossier d where d.profile.id = :id)")
                    .setParameter("id", profileId).executeUpdate();
        }
    }
}
