package com.finantrack.user.repository;

import com.finantrack.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** O e-mail é sempre gravado e consultado já normalizado em minúsculas. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
