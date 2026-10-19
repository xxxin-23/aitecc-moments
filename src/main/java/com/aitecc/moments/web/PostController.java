package com.aitecc.moments.web;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.User;
import com.aitecc.moments.service.AuthService;
import com.aitecc.moments.service.PostService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final AuthService authService;

    public PostController(PostService postService, AuthService authService) {
        this.postService = postService;
        this.authService = authService;
    }

    /**
     * 信息流。支持 {@code ?topic=美食} 按话题过滤、{@code ?userId=} 看某人的主页。
     * 未登录也能看（首页本来就要对外），likes 之类需要身份的操作另走。
     */
    @GetMapping
    public Feed.PageView<Feed.PostView> feed(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        User current = authService.resolve(token).orElse(null);
        return postService.feed(topic, userId, page, size, current);
    }

    @GetMapping("/topics")
    public List<String> topics() {
        return postService.topics();
    }

    @GetMapping("/{id}")
    public Feed.PostView detail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        User current = authService.resolve(token).orElse(null);
        return postService.detail(id, current);
    }

    /**
     * 发布动态：multipart 表单，文字 + 最多 9 张图 + 定位。
     *
     * <p>文本字段必须用 {@code @RequestParam} 而不是 {@code @RequestPart} ——
     * 浏览器 FormData 给字符串字段不带 Content-Type（默认 application/octet-stream），
     * 用 @RequestPart 会直接 415/500，这个坑很容易在真机上才暴露。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Feed.PostView create(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        User author = authService.require(token);
        return postService.create(author, content, topic, locationName, latitude, longitude, images);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestHeader("X-Auth-Token") String token) {
        User current = authService.require(token);
        postService.delete(id, current);
    }
}
