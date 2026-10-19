package com.aitecc.moments.web;

import com.aitecc.moments.dto.Auth;
import com.aitecc.moments.model.User;
import com.aitecc.moments.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册成功直接返回令牌，省掉「注册完还得再登录一次」这一步。 */
    @PostMapping("/register")
    public Auth.AuthResponse register(@Valid @RequestBody Auth.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public Auth.AuthResponse login(@Valid @RequestBody Auth.LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return authService
                .resolve(token)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(Auth.UserBrief.of(u)))
                .orElseGet(() -> ResponseEntity.status(401).body(java.util.Map.of("error", "未登录")));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        authService.logout(user);
    }

    @PatchMapping("/profile")
    public Auth.UserBrief updateProfile(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody Auth.UpdateProfileRequest req) {
        User user = authService.require(token);
        return authService.updateProfile(user, req);
    }
}
