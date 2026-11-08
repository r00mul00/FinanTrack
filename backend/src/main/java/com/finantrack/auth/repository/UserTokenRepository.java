package com.finantrack.auth.repository;

import com.finantrack.auth.entity.UserToken;
import com.finantrack.auth.entity.UserTokenType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    /** O lock garante o uso único mesmo com requisições simultâneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserToken t where t.tokenHash = :hash and t.type = :type")
    Optional<UserToken> findForUpdate(@Param("hash") String hash, @Param("type") UserTokenType type);

    @Modifying
    @Query("""
            update UserToken t
               set t.usedAt = :now, t.updatedAt = :now
             where t.user.id = :userId and t.type = :type and t.usedAt is null
            """)
    int invalidateActive(@Param("userId") UUID userId, @Param("type") UserTokenType type, @Param("now") Instant now);
}
