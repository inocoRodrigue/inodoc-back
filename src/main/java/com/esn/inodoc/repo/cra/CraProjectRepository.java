package com.esn.inodoc.repo.cra;

import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CraProjectRepository extends JpaRepository<CraProject, UUID> {
    Optional<CraProject> findByCode(String code);

        @Query("select p from CraProject p where p.archived = false order by p.type, p.name")
        List<CraProject> findAllByOrderByTypeAscNameAsc();

    boolean existsByNameIgnoreCaseAndType(String name, CraProjectType type);

    @Query("select distinct p from CraProject p left join p.collaborators c "
            + "where p.archived = false and (p.type <> :clientType or c.id = :userId) order by p.type, p.name")
    List<CraProject> findAvailableForUser(@Param("userId") UUID userId,
                                          @Param("clientType") CraProjectType clientType);
}