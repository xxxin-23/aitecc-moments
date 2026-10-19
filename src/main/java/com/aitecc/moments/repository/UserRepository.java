package com.aitecc.moments.repository;

import com.aitecc.moments.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    Optional<User> findByToken(String token);

    boolean existsByNickname(String nickname);
}
