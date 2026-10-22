package com.aitecc.moments.dto;

import com.aitecc.moments.model.Comment;
import com.aitecc.moments.model.Post;
import com.aitecc.moments.model.PostImage;
import java.time.LocalDateTime;
import java.util.List;

/** 动态 / 评论 / 分页的响应模型。 */
public final class Feed {

    private Feed() {}

    /** 图片相对 URL + 原始名，前端直接拿去做预览。 */
    public record ImageView(String url, String originName) {

        public static ImageView of(PostImage img) {
            return new ImageView(img.getUrl(), img.getOriginName());
        }
    }

    public record LocationView(String name, Double latitude, Double longitude) {

        public static LocationView of(String name, Double lat, Double lng) {
            // 没定位就别返回一串 null，前端判断会干净很多
            if ((name == null || name.isEmpty()) && lat == null) {
                return null;
            }
            return new LocationView(name == null ? "" : name, lat, lng);
        }
    }

    public record PostView(
            Long id,
            Auth.UserBrief author,
            String content,
            String topic,
            LocationView location,
            List<ImageView> images,
            LocalDateTime createdAt,
            long likeCount,
            long commentCount,
            boolean liked,
            boolean mine) {

        public static PostView of(Post p, long likeCount, long commentCount, boolean liked, Long currentUserId) {
            boolean mine = currentUserId != null && p.getAuthor().getId().equals(currentUserId);
            return new PostView(
                    p.getId(),
                    Auth.UserBrief.of(p.getAuthor()),
                    p.getContent(),
                    p.getTopic(),
                    LocationView.of(p.getLocationName(), p.getLatitude(), p.getLongitude()),
                    p.getImages().stream().map(ImageView::of).toList(),
                    p.getCreatedAt(),
                    likeCount,
                    commentCount,
                    liked,
                    mine);
        }
    }

    public record CommentView(Long id, Auth.UserBrief author, String content, LocalDateTime createdAt, boolean mine) {

        public static CommentView of(Comment c, Long currentUserId) {
            boolean mine = currentUserId != null && c.getAuthor().getId().equals(currentUserId);
            return new CommentView(
                    c.getId(), Auth.UserBrief.of(c.getAuthor()), c.getContent(), c.getCreatedAt(), mine);
        }
    }

    public record CommentRequest(String content) {}

    /** 编辑动态的请求体：只改文字 / 话题 / 定位，图片不动。 */
    public record PostEditRequest(
            String content, String topic, String locationName, Double latitude, Double longitude) {}

    public record PageView<T>(List<T> items, int page, int size, boolean hasNext) {}
}
