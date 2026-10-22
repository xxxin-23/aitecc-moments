package com.aitecc.moments.service;

import com.aitecc.moments.dto.Auth;
import com.aitecc.moments.model.User;
import com.aitecc.moments.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册 / 登录 / 令牌校验。 */
@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    public Auth.AuthResponse register(Auth.RegisterRequest req) {
        String nickname = normalizeNickname(req.nickname());
        if (users.existsByNickname(nickname)) {
            throw new BizException("这个昵称已经被占了，换一个吧");
        }
        User user = new User();
        user.setNickname(nickname);
        user.setPasswordHash(hasher.hash(req.password()));
        if (req.avatar() != null && !req.avatar().isBlank()) {
            user.setAvatar(req.avatar().strip().substring(0, Math.min(8, req.avatar().strip().length())));
        }
        user.setToken(newToken());
        users.save(user);
        return new Auth.AuthResponse(user.getToken(), Auth.UserBrief.of(user));
    }

    public Auth.AuthResponse login(Auth.LoginRequest req) {
        String nickname = normalizeNickname(req.nickname());
        User user =
                users.findByNickname(nickname)
                        .orElseThrow(() -> BizException.unauthorized("昵称或密码不对"));
        if (!hasher.matches(req.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("昵称或密码不对");
        }
        user.setToken(newToken());
        users.save(user);
        return new Auth.AuthResponse(user.getToken(), Auth.UserBrief.of(user));
    }

    public void logout(User user) {
        user.setToken(null);
        users.save(user);
    }

    /**
     * 改个人资料。昵称 / 头像 / 简介都是「传了才改」，互不牵连。
     *
     * <p>昵称走和注册同一套规则（2~24 字），但查重要排除自己 ——
     * 否则「只改个头像、昵称原样提交」会被自己撞名挡下来。
     */
    public Auth.UserBrief updateProfile(User user, Auth.UpdateProfileRequest req) {
        if (req.nickname() != null && !req.nickname().isBlank()) {
            String nickname = normalizeNickname(req.nickname());
            if (users.existsByNicknameAndIdNot(nickname, user.getId())) {
                throw new BizException("这个昵称已经被占了，换一个吧");
            }
            user.setNickname(nickname);
        }
        if (req.avatar() != null && !req.avatar().isBlank()) {
            user.setAvatar(req.avatar().strip().substring(0, Math.min(8, req.avatar().strip().length())));
        }
        if (req.bio() != null) {
            user.setBio(req.bio().length() > 60 ? req.bio().substring(0, 60) : req.bio());
        }
        users.save(user);
        return Auth.UserBrief.of(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return users.findByToken(token.trim());
    }

    /** 需要登录的接口用这个：拿不到用户直接 401，controller 里就不用判空了。 */
    @Transactional(readOnly = true)
    public User require(String token) {
        return resolve(token).orElseThrow(() -> BizException.unauthorized("请先登录"));
    }

    @Transactional(readOnly = true)
    public Auth.UserBrief profile(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> BizException.notFound("用户不存在"));
        return Auth.UserBrief.of(user);
    }

    private String normalizeNickname(String raw) {
        if (raw == null) {
            throw new BizException("昵称不能为空");
        }
        String nickname = raw.trim();
        if (nickname.length() < 2 || nickname.length() > 24) {
            throw new BizException("昵称长度要在 2~24 个字符之间");
        }
        return nickname;
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
