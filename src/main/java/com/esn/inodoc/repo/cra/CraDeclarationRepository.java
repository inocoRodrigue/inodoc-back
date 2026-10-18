package com.esn.inodoc.repo.cra;

import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CraDeclarationRepository extends JpaRepository<CraDeclaration, UUID>, JpaSpecificationExecutor<CraDeclaration> {
    Optional<CraDeclaration> findByCollaborator_IdAndYearAndMonth(UUID userId, int year, int month);

    List<CraDeclaration> findByCollaborator_IdOrderByYearDescMonthDesc(UUID userId);

    Optional<CraDeclaration> findByIdAndCollaborator_Id(UUID id, UUID userId);

        @Query("select distinct d from CraDeclaration d join fetch d.collaborator left join fetch d.entries e "
            + "left join fetch e.project left join fetch e.afternoonProject where d.year = :year and d.month = :month and d.collaborator.id in :users")
        List<CraDeclaration> findMonthlyForUsers(@Param("year") int year, @Param("month") int month, @Param("users") List<UUID> users);

        @Query("select distinct d from CraDeclaration d join fetch d.collaborator left join fetch d.validatedBy "
            + "left join fetch d.entries e left join fetch e.project left join fetch e.afternoonProject where d.id in :ids")
        List<CraDeclaration> findWithDetailsByIdIn(@Param("ids") List<UUID> ids);

        @Query("select distinct d.year from CraDeclaration d where d.status = :status order by d.year desc")
        List<Integer> findYearsByStatus(@Param("status") CraDeclarationStatus status);

        @Query("select distinct new com.esn.inodoc.dto.cra.CraCollaboratorDto(u.id, u.firstName, u.lastName, u.email) "
            + "from CraDeclaration d join d.collaborator u where d.status = :status order by u.firstName, u.lastName")
        List<com.esn.inodoc.dto.cra.CraCollaboratorDto> findCollaboratorsByStatus(@Param("status") CraDeclarationStatus status);

        @Query("select distinct concat(concat(u.firstName, ' '), u.lastName) from CraDeclaration d "
            + "join d.validatedBy u where d.status = :status order by 1")
        List<String> findValidatorsByStatus(@Param("status") CraDeclarationStatus status);

        @Query("select distinct new com.esn.inodoc.dto.cra.CraProjectOptionDto(p.id, p.name) "
            + "from CraDeclaration d join d.entries e join e.project p where d.status = :status order by p.name")
        List<com.esn.inodoc.dto.cra.CraProjectOptionDto> findProjectsByStatus(@Param("status") CraDeclarationStatus status);

        @Query("select distinct new com.esn.inodoc.dto.cra.CraProjectOptionDto(p.id, p.name) "
            + "from CraDeclaration d join d.entries e join e.afternoonProject p where d.status = :status order by p.name")
        List<com.esn.inodoc.dto.cra.CraProjectOptionDto> findAfternoonProjectsByStatus(@Param("status") CraDeclarationStatus status);

        @Query("select distinct d from CraDeclaration d join fetch d.collaborator "
            + "left join fetch d.entries e left join fetch e.project "
                    + "where d.status = :declaredStatus order by d.year, d.month, d.createdAt")
                List<CraDeclaration> findForReview(@Param("declaredStatus") CraDeclarationStatus declaredStatus);
}