package com.aitecc.moments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 收藏 / 推荐。
 *
 * <p>和点赞（{@link Like}）分开建表是因为点赞早就上线了，不动它最稳；
 * 新增的两种互动共用这一张表，靠 {@code kind} 区分，后面再加「踩」「转发」只要加枚举值。
 * 联合唯一索引保证「同一人对同一动态的同一动作只有一条」，重复点击是幂等的。
 */
@Entity
@Table(
        name = "reactions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_reactions_post_user_kind",
                        columnNames = {"post_id", "user_id", "kind"}),
        indexes = {
            @Index(name = "idx_reactions_post", columnList = "post_id"),
            @Index(name = "idx_reactions_user_kind", columnList = "user_id, kind")
        })
public class Reaction {

    public enum Kind {
        /** 收藏：自己留着慢慢看 */
        FAVORITE,
        /** 推荐：安利给社团同学，权重最高，会把它顶上「推荐」流 */
        RECOMMEND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private Kind kind;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
