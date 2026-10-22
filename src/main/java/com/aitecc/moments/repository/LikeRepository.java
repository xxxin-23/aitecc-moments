package com.aitecc.moments.repository;

import com.aitecc.moments.model.Like;
import com.aitecc.moments.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    /** 详情页展示「谁点过赞」用 */
    List<Like> findTop20ByPostIdOrderByCreatedAtAsc(Long postId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    /** 删动态时一并清掉，否则外键约束会让删除失败 */
    void deleteByPostId(Long postId);
}
