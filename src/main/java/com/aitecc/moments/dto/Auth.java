package com.aitecc.moments.dto;

import com.aitecc.moments.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 认证相关的请求 / 响应模型。 */
public final class Auth {

    private Auth() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 24) String nickname,
            @NotBlank @Size(min = 6, max = 64) String password,
            String avatar) {}

    public record LoginRequest(@NotBlank String nickname, @NotBlank String password) {}

    /**
     * 个人资料：昵称 / 头像 / 一句话简介都能改。
     *
     * <p>三个字段都是可选的 —— 传 null 表示「这项不改」，只有显式传了值才校验，
     * 这样前端「只换个头像」也不会被昵称规则挡住。
     */
    public record UpdateProfileRequest(String nickname, String avatar, String bio) {}

    public record AuthResponse(String token, UserBrief user) {}

    public record UserBrief(Long id, String nickname, String avatar, String bio, Integer postCount) {

        public static UserBrief of(User u) {
            return new UserBrief(u.getId(), u.getNickname(), u.getAvatar(), u.getBio(), null);
        }

        public static UserBrief of(User u, int postCount) {
            return new UserBrief(u.getId(), u.getNickname(), u.getAvatar(), u.getBio(), postCount);
        }
    }
}
