package com.aitecc.moments.repository;

import com.aitecc.moments.model.Reaction;
import com.aitecc.moments.model.Reaction.Kind;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByPostIdAndUserIdAndKind(Long postId, Long userId, Kind kind);

    long countByPostIdAndKind(Long postId, Kind kind);

    long countByUserIdAndKind(Long userId, Kind kind);

    void deleteByPostIdAndUserIdAndKind(Long postId, Long userId, Kind kind);

    /** 删动态时一并清掉，否则外键会报错 */
    void deleteByPostId(Long postId);

    /** 「我的收藏」按收藏时间倒序 */
    Slice<Reaction> findByUserIdAndKindOrderByCreatedAtDesc(Long userId, Kind kind, Pageable pageable);
}
