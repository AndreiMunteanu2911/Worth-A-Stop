package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.entity.Review;
import com.locationreview.location_review_platform.entity.ReviewComment;
import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.ReviewCommentRepository;
import com.locationreview.location_review_platform.repository.ReviewLikeRepository;
import com.locationreview.location_review_platform.repository.ReviewRepository;
import com.locationreview.location_review_platform.repository.UserRepository;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.ReviewInteractionService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewInteractionServiceTests {
    @Mock ReviewRepository reviews;
    @Mock ReviewLikeRepository likes;
    @Mock ReviewCommentRepository comments;
    @Mock UserRepository users;
    @InjectMocks ReviewInteractionService interactions;

    @Test
    void writesLikesForTheCallerAndCountsAfterInsertion() {
        when(reviews.findForInteraction(5L)).thenReturn(Optional.of(new Review()));
        when(likes.countByReviewId(5L)).thenReturn(3L);

        assertThat(interactions.like(2L, 5L)).isEqualTo(new ReviewInteractionService.LikeState(true, 3));
        interactions.unlike(2L, 5L);

        var order = inOrder(reviews, likes);
        order.verify(reviews).findForInteraction(5L);
        order.verify(likes).likeIfAbsent(eq(5L), eq(2L), any(Instant.class));
        order.verify(likes).countByReviewId(5L);
        order.verify(reviews).findForInteraction(5L);
        order.verify(likes).unlike(5L, 2L);
    }

    @Test
    void validatesCommentTextAndPaginationBeforeReadingOrWriting() {
        assertThatThrownBy(() -> interactions.addComment(2L, 5L, "\n\u2003"))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.fieldErrors()).containsKey("body"));
        assertThatThrownBy(() -> interactions.addComment(2L, 5L, "x".repeat(1001)))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> interactions.comments(5L, Integer.MAX_VALUE, 50))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("invalid_pagination"));
        verifyNoInteractions(reviews, likes, comments, users);
    }

    @Test
    void missingReviewPreventsInteractionWrites() {
        assertThatThrownBy(() -> interactions.like(2L, 9L))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("review_not_found"));
        assertThatThrownBy(() -> interactions.addComment(2L, 9L, "Thanks"))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(likes, comments, users);
    }

    @Test
    void commentDeletionRequiresTheCommentAuthor() {
        ReviewComment comment = new ReviewComment();
        User author = mock(User.class);
        Review parent = mock(Review.class);
        when(author.getId()).thenReturn(2L);
        when(parent.getId()).thenReturn(5L);
        comment.setAuthor(author);
        comment.setReview(parent);
        when(comments.findForDeletion(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> interactions.deleteComment(3L, 1L))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(comments, never()).delete(any());
        assertThat(interactions.deleteComment(2L, 1L)).isEqualTo(5L);
        verify(comments).delete(comment);
    }
}
