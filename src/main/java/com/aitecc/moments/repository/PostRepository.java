package com.aitecc.moments.repository;

import com.aitecc.moments.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 全部动态，按时间倒序。Slice 比 Page 便宜：不查 count。 */
    Slice<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按话题过滤 */
    Slice<Post> findByTopicOrderByCreatedAtDesc(String topic, Pageable pageable);

    /** 某人的主页 */
    Slice<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    long countByAuthorId(Long authorId);
}
