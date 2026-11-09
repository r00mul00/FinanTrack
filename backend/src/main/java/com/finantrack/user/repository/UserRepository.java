package com.finantrack.user.repository;

import com.finantrack.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** O e-mail é sempre gravado e consultado já normalizado em minúsculas. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("""
            update User u
               set u.deletionRequestedAt = null, u.updatedAt = :now
             where u.id = :id and u.deletionRequestedAt is not null
            """)
    int cancelDeletionRequest(@Param("id") UUID id, @Param("now") Instant now);

    @Query("select u.id from User u where u.deletionRequestedAt <= :limit")
    List<UUID> findIdsWithDeletionRequestedUntil(@Param("limit") Instant limit);

    @Query("select u.id from User u where u.notifyBudget = false")
    Set<UUID> findIdsWithBudgetNoticesOff();

    @Query("select u.id from User u where u.notifyDueDates = false")
    Set<UUID> findIdsWithDueDateNoticesOff();
}
