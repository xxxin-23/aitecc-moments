package com.aitecc.moments.repository;

import com.aitecc.moments.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    Optional<User> findByToken(String token);

    boolean existsByNickname(String nickname);

    /** 改昵称时用来查重：同名但就是自己，不算冲突 */
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
