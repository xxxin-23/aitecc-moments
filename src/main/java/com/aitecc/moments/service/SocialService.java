package com.aitecc.moments.service;

import com.aitecc.moments.dto.Feed;
import com.aitecc.moments.model.Comment;
import com.aitecc.moments.model.Like;
import com.aitecc.moments.model.Post;
import com.aitecc.moments.model.User;
import com.aitecc.moments.repository.CommentRepository;
import com.aitecc.moments.repository.LikeRepository;
import com.aitecc.moments.repository.PostRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 评论与点赞。 */
@Service
@Transactional
public class SocialService {

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LikeRepository likes;

    public SocialService(PostRepository posts, CommentRepository comments, LikeRepository likes) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
    }

    public Feed.CommentView addComment(User author, Long postId, String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new BizException("评论最多 500 字");
        }

        Post post = posts.findById(postId).orElseThrow(() -> BizException.notFound("动态不存在"));
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(content);
        Comment saved = comments.save(comment);
        return Feed.CommentView.of(saved, author.getId());
    }

    @Transactional(readOnly = true)
    public List<Feed.CommentView> listComments(Long postId, User current) {
        Long me = current == null ? null : current.getId();
        return comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> Feed.CommentView.of(c, me))
                .toList();
    }

    public void deleteComment(User current, Long commentId) {
        Comment comment =
                comments.findById(commentId).orElseThrow(() -> BizException.notFound("评论不存在"));
        if (!comment.getAuthor().getId().equals(current.getId())) {
            throw new BizException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "只能删自己的评论");
        }
        comments.delete(comment);
    }

    /** 幂等点赞：已经点过就原样返回，不报错。 */
    public void like(User user, Long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> BizException.notFound("动态不存在"));
        if (likes.findByPostIdAndUserId(postId, user.getId()).isEmpty()) {
            Like like = new Like();
            like.setPost(post);
            like.setUser(user);
            likes.save(like);
        }
    }

    /** 幂等取消：本来就没点赞也不报错。 */
    public void unlike(User user, Long postId) {
        likes.deleteByPostIdAndUserId(postId, user.getId());
    }

    public record ToggleResult(boolean liked, long count) {}

    /** 点赞 / 取消共用入口，返回最新状态，省得前端再拉一次详情。 */
    public ToggleResult toggle(User user, Long postId, boolean wantLike) {
        if (wantLike) {
            like(user, postId);
        } else {
            unlike(user, postId);
        }
        return new ToggleResult(wantLike, likes.countByPostId(postId));
    }
}
