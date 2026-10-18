package com.esn.inodoc.repo;

import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockForCraReminder(@Param("id") UUID id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByProfileId(UUID profileId);

    @Query("select u from User u left join fetch u.profile where u.id in :ids")
    List<User> findWithProfileByIdIn(@Param("ids") List<UUID> ids);

    @Query("select distinct u from User u join u.roles r where u.enabled = true and r.name = :role order by u.lastName, u.firstName")
    List<User> findEnabledWithRole(@Param("role") RoleName role);

    @Query("select count(u) from User u join u.roles r where u.enabled = true and r.name = :role")
    long countEnabledByRole(@Param("role") RoleName role);
}
