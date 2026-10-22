package com.aitecc.moments.web;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.Reaction;
import com.aitecc.moments.model.User;
import com.aitecc.moments.service.AuthService;
import com.aitecc.moments.service.PostService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SocialController {

    private final SocialService socialService;
    private final AuthService authService;
    private final PostService postService;

    public SocialController(
            SocialService socialService, AuthService authService, PostService postService) {
        this.socialService = socialService;
        this.authService = authService;
        this.postService = postService;
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

    /** 收藏：自己留着慢慢看。POST = 收藏，DELETE = 取消。 */
    @PostMapping("/posts/{id}/favorite")
    public Feed.ToggleView favorite(@PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggleReaction(user, id, Reaction.Kind.FAVORITE, true);
    }

    @DeleteMapping("/posts/{id}/favorite")
    public Feed.ToggleView unfavorite(
            @PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggleReaction(user, id, Reaction.Kind.FAVORITE, false);
    }

    /** 推荐：安利给社团同学，会把它顶上「推荐」流。 */
    @PostMapping("/posts/{id}/recommend")
    public Feed.ToggleView recommend(
            @PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggleReaction(user, id, Reaction.Kind.RECOMMEND, true);
    }

    @DeleteMapping("/posts/{id}/recommend")
    public Feed.ToggleView unrecommend(
            @PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return socialService.toggleReaction(user, id, Reaction.Kind.RECOMMEND, false);
    }

    /** 「我的收藏」列表，复用信息流的分页结构。 */
    @GetMapping("/me/favorites")
    public Feed.PageView<Feed.PostView> myFavorites(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = authService.require(token);
        return postService.favorites(user, page, size);
    }

    /** 个人页顶部的「收藏 / 推荐」计数。 */
    @GetMapping("/me/stats")
    public Map<String, Object> myStats(@RequestHeader("X-Auth-Token") String token) {
        User user = authService.require(token);
        return Map.of(
                "favoriteCount", socialService.countMyReactions(user, Reaction.Kind.FAVORITE),
                "recommendCount", socialService.countMyReactions(user, Reaction.Kind.RECOMMEND));
    }

    /** 未登录访问需要身份的接口时，给个明确的提示而不是 500。 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "app", "aitecc-moments");
    }
}
