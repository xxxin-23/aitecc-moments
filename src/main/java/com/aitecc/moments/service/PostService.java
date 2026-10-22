package com.aitecc.moments.service;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.Post;
import com.aitecc.moments.model.PostImage;
import com.aitecc.moments.model.User;
import com.aitecc.moments.repository.CommentRepository;
import com.aitecc.moments.repository.LikeRepository;
import com.aitecc.moments.repository.PostRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 动态的发布、浏览、删除。 */
@Service
@Transactional
public class PostService {

    private static final Pattern TOPIC = Pattern.compile("^[^,\\s#]{1,32}$");

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final FileStorage storage;

    @Value("${moments.max-images-per-post}")
    private int maxImages;

    public PostService(
            PostRepository posts,
            CommentRepository comments,
            LikeRepository likes,
            FileStorage storage) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.storage = storage;
    }

    public Feed.PostView create(
            User author,
            String content,
            String topic,
            String locationName,
            Double latitude,
            Double longitude,
            List<MultipartFile> images) {
        String text = content == null ? "" : content.strip();
        if (text.length() > 2000) {
            throw new BizException("正文最多 2000 字");
        }
        if (text.isEmpty() && (images == null || images.isEmpty())) {
            throw new BizException("写点什么，或者至少发张图");
        }

        List<MultipartFile> files = images == null ? List.of() : images;
        if (files.size() > maxImages) {
            throw new BizException("一张动态最多 " + maxImages + " 张图");
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setContent(text);
        post.setTopic(normalizeTopic(topic));
        post.setLocationName(locationName == null ? "" : locationName.strip());
        post.setLatitude(latitude);
        post.setLongitude(longitude);

        // 先落盘图片再落库：图片失败时不留下半成品动态
        for (MultipartFile file : files) {
            String url = storage.save(file);
            post.addImage(new PostImage(url, safeName(file.getOriginalFilename()), file.getSize()));
        }

        Post saved = posts.save(post);
        return Feed.PostView.of(saved, 0, 0, false, author.getId());
    }

    @Transactional(readOnly = true)
    public Feed.PageView<Feed.PostView> feed(String topic, Long authorId, int page, int size, User current) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        var pageable =
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Slice<Post> slice =
                topic != null && !topic.isBlank()
                        ? posts.findByTopicOrderByCreatedAtDesc(topic.strip(), pageable)
                        : authorId != null
                                ? posts.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable)
                                : posts.findAllByOrderByCreatedAtDesc(pageable);

        Long me = current == null ? null : current.getId();
        List<Feed.PostView> items =
                slice.getContent().stream().map(p -> toView(p, me)).toList();
        return new Feed.PageView<>(items, safePage, safeSize, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public Feed.PostView detail(Long id, User current) {
        Post post = posts.findById(id).orElseThrow(() -> BizException.notFound("动态不存在或已删除"));
        return toView(post, current == null ? null : current.getId());
    }

    @Transactional(readOnly = true)
    public List<String> topics() {
        return List.of("美食", "探店", "学习", "运动", "风景", "吐槽", "数码", "其他");
    }

    /**
     * 编辑自己发的动态。只动文字 / 话题 / 定位 —— 图片要换得先删重发，
     * 这样服务端不用处理「新旧图片混合」的一堆边界。
     */
    public Feed.PostView update(
            Long id,
            User current,
            String content,
            String topic,
            String locationName,
            Double latitude,
            Double longitude) {
        Post post = posts.findById(id).orElseThrow(() -> BizException.notFound("动态不存在"));
        if (!post.getAuthor().getId().equals(current.getId())) {
            throw new BizException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "只能编辑自己发的动态");
        }

        String text = content == null ? "" : content.strip();
        if (text.length() > 2000) {
            throw new BizException("正文最多 2000 字");
        }
        if (text.isEmpty() && post.getImages().isEmpty()) {
            throw new BizException("写点什么，或者至少留一张图");
        }

        post.setContent(text);
        post.setTopic(normalizeTopic(topic));
        post.setLocationName(locationName == null ? "" : locationName.strip());
        post.setLatitude(latitude);
        post.setLongitude(longitude);

        Post saved = posts.save(post);
        return toView(saved, current.getId());
    }

    public void delete(Long id, User current) {
        Post post = posts.findById(id).orElseThrow(() -> BizException.notFound("动态不存在"));
        if (!post.getAuthor().getId().equals(current.getId())) {
            throw new BizException(org.springframework.http.HttpStatus.FORBIDDEN, "只能删自己发的动态");
        }
        posts.delete(post);
    }

    @Transactional(readOnly = true)
    public Feed.PostView toView(Post post, Long currentUserId) {
        long likeCount = likes.countByPostId(post.getId());
        long commentCount = comments.countByPostId(post.getId());
        boolean liked =
                currentUserId != null && likes.findByPostIdAndUserId(post.getId(), currentUserId).isPresent();
        return Feed.PostView.of(post, likeCount, commentCount, liked, currentUserId);
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return "";
        }
        String t = topic.strip();
        if (!TOPIC.matcher(t).matches()) {
            throw new BizException("话题不能包含空格或逗号");
        }
        return t;
    }

    private String safeName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
