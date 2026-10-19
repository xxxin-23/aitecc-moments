package com.aitecc.moments.web;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.User;
import com.aitecc.moments.service.AuthService;
import com.aitecc.moments.service.SocialService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SocialController {

    private final SocialService socialService;
    private final AuthService authService;

    public SocialController(SocialService socialService, AuthService authService) {
        this.socialService = socialService;
        this.authService = authService;
    }

    @GetMapping("/posts/{id}/comments")
    public List<Feed.CommentView> comments(
            @PathVariable Long id,
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        User current = authService.resolve(token).orElse(null);
        return socialService.listComments(id, current);
    }

    @PostMapping("/posts/{id}/comments")
    public Feed.CommentView addComment(
            @PathVariable Long id,
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody Feed.CommentRequest req) {
        User author = authService.require(token);
        return socialService.addComment(author, id, req.content());
    }

    @DeleteMapping("/comments/{id}")
    public void deleteComment(@PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User current = authService.require(token);
        socialService.deleteComment(current, id);
    }

    /** 点赞 / 取消，返回 {@code {liked, count}} 让前端直接刷新图标和数字。 */
    @PostMapping("/posts/{id}/like")
    public SocialService.ToggleResult like(
            @PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggle(user, id, true);
    }

    @DeleteMapping("/posts/{id}/like")
    public SocialService.ToggleResult unlike(
            @PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggle(user, id, false);
    }

    /** 未登录访问需要身份的接口时，给个明确的提示而不是 500。 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "app", "aitecc-moments");
    }
}
