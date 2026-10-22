package com.aitecc.moments.repository;

import com.aitecc.moments.model.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostId(Long postId);

    /** 删动态时一并清掉，否则外键约束会让删除失败 */
    void deleteByPostId(Long postId);
}
