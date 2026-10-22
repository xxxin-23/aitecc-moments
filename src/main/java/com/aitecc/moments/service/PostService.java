package com.aitecc.moments.service;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.Post;
import com.aitecc.moments.model.PostImage;
import com.aitecc.moments.model.Reaction;
import com.aitecc.moments.model.User;
import com.aitecc.moments.repository.CommentRepository;
import com.aitecc.moments.repository.LikeRepository;
import com.aitecc.moments.repository.PostRepository;
import com.aitecc.moments.repository.ReactionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 动态的发布、浏览、编辑、删除，以及「推荐 / 最新 / 我的收藏」三种信息流。 */
@Service
@Transactional
public class PostService {

    private static final Pattern TOPIC = Pattern.compile("^[^,\\s#]{1,32}$");

    /** 推荐流的热度权重：推荐票最值钱，收藏和评论次之，点赞最轻。 */
    private static final double W_LIKE = 1.0;
    private static final double W_COMMENT = 2.0;
    private static final double W_FAVORITE = 2.0;
    private static final double W_RECOMMEND = 3.0;

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final ReactionRepository reactions;
    private final FileStorage storage;

    @Value("${moments.max-images-per-post}")
    private int maxImages;

    public PostService(
            PostRepository posts,
            CommentRepository comments,
            LikeRepository likes,
            ReactionRepository reactions,
            FileStorage storage) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.reactions = reactions;
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
        return toView(saved, author.getId());
    }

    /**
     * 信息流。
     *
     * @param sort {@code recommend} 按热度推荐（默认），{@code latest} 按时间倒序
     */
    @Transactional(readOnly = true)
    public Feed.PageView<Feed.PostView> feed(
            String topic, Long authorId, int page, int size, User current, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Long me = current == null ? null : current.getId();

        boolean byTime = !"recommend".equalsIgnoreCase(sort);
        if (!byTime && isBlank(topic) && authorId == null) {
            return recommendFeed(safePage, safeSize, me);
        }

        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Slice<Post> slice =
                !isBlank(topic)
                        ? posts.findByTopicOrderByCreatedAtDesc(topic.strip(), pageable)
                        : authorId != null
                                ? posts.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable)
                                : posts.findAllByOrderByCreatedAtDesc(pageable);

        List<Feed.PostView> items = slice.getContent().stream().map(p -> toView(p, me)).toList();
        return new Feed.PageView<>(items, safePage, safeSize, slice.hasNext());
    }

    /** 「我的收藏」：只列自己收藏过的，按收藏时间倒序。 */
    @Transactional(readOnly = true)
    public Feed.PageView<Feed.PostView> favorites(User current, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Slice<Reaction> slice =
                reactions.findByUserIdAndKindOrderByCreatedAtDesc(
                        current.getId(), Reaction.Kind.FAVORITE, pageable);
        Long me = current.getId();
        List<Feed.PostView> items =
                slice.getContent().stream().map(r -> toView(r.getPost(), me)).toList();
        return new Feed.PageView<>(items, safePage, safeSize, slice.hasNext());
    }

    /**
     * 推荐流：热度 / 时间衰减，越新的内容越容易上榜。
     *
     * <p>社团规模（几百到几千条）直接内存排序最省事，也方便随时调权重；
     * 真到几十万条再换物化视图或离线算分。
     */
    @Transactional(readOnly = true)
    public Feed.PageView<Feed.PostView> recommendFeed(int page, int size, Long currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        List<Post> all = posts.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> ranked =
                all.stream()
                        .sorted(
                                Comparator.comparingDouble((Post p) -> -heat(p, now))
                                        .thenComparing(Post::getCreatedAt, Comparator.reverseOrder()))
                        .toList();

        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<Feed.PostView> items =
                ranked.subList(from, to).stream().map(p -> toView(p, currentUserId)).toList();
        return new Feed.PageView<>(items, page, size, to < ranked.size());
    }

    private double heat(Post post, LocalDateTime now) {
        long likeCount = likes.countByPostId(post.getId());
        long commentCount = comments.countByPostId(post.getId());
        long favoriteCount = reactions.countByPostIdAndKind(post.getId(), Reaction.Kind.FAVORITE);
        long recommendCount = reactions.countByPostIdAndKind(post.getId(), Reaction.Kind.RECOMMEND);
        double score =
                likeCount * W_LIKE
                        + commentCount * W_COMMENT
                        + favoriteCount * W_FAVORITE
                        + recommendCount * W_RECOMMEND;
        double hours = Math.max(1.0, Duration.between(post.getCreatedAt(), now).toHours()) + 2.0;
        return score / Math.pow(hours, 0.6);
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

    /**
     * 删动态。
     *
     * <p>点赞 / 评论 / 收藏 / 推荐都引用了 post_id，直接删帖会撞外键。
     * 所以在删帖前先清掉这些附属记录 —— 顺序不能反，否则同学点赞过的动态就删不掉了。
     */
    public void delete(Long id, User current) {
        Post post = posts.findById(id).orElseThrow(() -> BizException.notFound("动态不存在"));
        if (!post.getAuthor().getId().equals(current.getId())) {
            throw new BizException(org.springframework.http.HttpStatus.FORBIDDEN, "只能删自己发的动态");
        }
        likes.deleteByPostId(id);
        reactions.deleteByPostId(id);
        comments.deleteByPostId(id);
        posts.delete(post);
    }

    @Transactional(readOnly = true)
    public Feed.PostView toView(Post post, Long currentUserId) {
        long likeCount = likes.countByPostId(post.getId());
        long commentCount = comments.countByPostId(post.getId());
        long favoriteCount = reactions.countByPostIdAndKind(post.getId(), Reaction.Kind.FAVORITE);
        long recommendCount = reactions.countByPostIdAndKind(post.getId(), Reaction.Kind.RECOMMEND);
        boolean liked =
                currentUserId != null && likes.findByPostIdAndUserId(post.getId(), currentUserId).isPresent();
        boolean favorited =
                currentUserId != null
                        && reactions
                                .findByPostIdAndUserIdAndKind(
                                        post.getId(), currentUserId, Reaction.Kind.FAVORITE)
                                .isPresent();
        boolean recommended =
                currentUserId != null
                        && reactions
                                .findByPostIdAndUserIdAndKind(
                                        post.getId(), currentUserId, Reaction.Kind.RECOMMEND)
                                .isPresent();
        return Feed.PostView.of(
                post,
                likeCount,
                commentCount,
                favoriteCount,
                recommendCount,
                liked,
                favorited,
                recommended,
                currentUserId);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
