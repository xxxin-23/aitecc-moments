package com.aitecc.moments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 社团成员账号。昵称唯一，密码用 PBKDF2 加盐存储，明文从不落库。 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** 头像：直接存 emoji 或图片 URL，省掉一套头像文件管理。 */
    @Column(length = 500)
    private String avatar = "🙂";

    @Column(length = 60)
    private String bio = "";

    /** 登录令牌。MVP 用一个 UUID，登出即清空；多设备登录会相互挤掉，够社团规模用。 */
    @Column(length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (avatar == null || avatar.isBlank()) {
            avatar = "🙂";
        }
        if (bio == null) {
            bio = "";
        }
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
