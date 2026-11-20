package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.entity.Review;
import com.locationreview.location_review_platform.entity.ReviewComment;
import com.locationreview.location_review_platform.repository.ReviewCommentRepository;
import com.locationreview.location_review_platform.repository.ReviewLikeRepository;
import com.locationreview.location_review_platform.repository.ReviewRepository;
import com.locationreview.location_review_platform.repository.UserRepository;
import com.locationreview.location_review_platform.service.ReviewService.Author;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewInteractionService {
    public record LikeState(boolean liked, long likeCount) {}
    public record CommentView(long id, long reviewId, Author author, String body, Instant createdAt) {}

    private final ReviewRepository reviews;
    private final ReviewLikeRepository likes;
    private final ReviewCommentRepository comments;
    private final UserRepository users;

    public ReviewInteractionService(ReviewRepository reviews, ReviewLikeRepository likes,
            ReviewCommentRepository comments, UserRepository users) {
        this.reviews = reviews;
        this.likes = likes;
        this.comments = comments;
        this.users = users;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeState like(long userId, long reviewId) {
        reviewForWrite(reviewId);
        likes.likeIfAbsent(reviewId, userId, Instant.now());
        return new LikeState(true, likes.countByReviewId(reviewId));
    }

    @Transactional
    public void unlike(long userId, long reviewId) {
        reviewForWrite(reviewId);
        likes.unlike(reviewId, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentView> comments(long reviewId, int page, int size) {
        positiveId(reviewId);
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "Page must be nonnegative with a supported offset, and size must be 1–50.", Map.of());
        }
        if (!reviews.existsById(reviewId)) {
            throw reviewNotFound();
        }
        return PageResponse.from(comments.findByReviewIdOrderByCreatedAtAscIdAsc(reviewId, PageRequest.of(page, size))
                .map(ReviewInteractionService::view));
    }

    @Transactional
    public CommentView addComment(long userId, long reviewId, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 1000) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields", "Check the comment text.",
                    Map.of("body", "Enter 1–1000 characters after trimming."));
        }
        Review review = reviewForWrite(reviewId);
        var author = users.findById(userId).orElseThrow(() -> new AccountException(
                HttpStatus.NOT_FOUND, "user_not_found", "User not found.", Map.of()));
        Instant now = Instant.now();
        ReviewComment comment = new ReviewComment();
        comment.setReview(review);
        comment.setAuthor(author);
        comment.setBody(text);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        return view(comments.save(comment));
    }

    @Transactional
    public long deleteComment(long userId, long commentId) {
        positiveId(commentId);
        ReviewComment comment = comments.findForDeletion(commentId).orElseThrow(() -> new AccountException(
                HttpStatus.NOT_FOUND, "comment_not_found", "Comment not found.", Map.of()));
        if (comment.getAuthor().getId() != userId) {
            throw new AccountException(HttpStatus.FORBIDDEN, "forbidden", "Only the author can delete this comment.", Map.of());
        }
        long reviewId = comment.getReview().getId();
        comments.delete(comment);
        return reviewId;
    }

    @Transactional(readOnly = true)
    public long commentCount(long reviewId) {
        return comments.countByReviewId(reviewId);
    }

    private Review reviewForWrite(long id) {
        positiveId(id);
        // Hold the parent row through the write so a concurrent review deletion cannot strand it.
        return reviews.findForInteraction(id).orElseThrow(ReviewInteractionService::reviewNotFound);
    }

    private static CommentView view(ReviewComment comment) {
        return new CommentView(comment.getId(), comment.getReview().getId(), Author.from(comment.getAuthor()),
                comment.getBody(), comment.getCreatedAt());
    }

    private static void positiveId(long id) {
        if (id <= 0) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields", "Check the resource ID.",
                    Map.of("id", "must be greater than 0"));
        }
    }

    private static AccountException reviewNotFound() {
        return new AccountException(HttpStatus.NOT_FOUND, "review_not_found", "Review not found.", Map.of());
    }
}
