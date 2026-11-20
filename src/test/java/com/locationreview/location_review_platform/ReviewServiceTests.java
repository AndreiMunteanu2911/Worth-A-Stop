package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.entity.Place;
import com.locationreview.location_review_platform.entity.Review;
import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.PlaceRepository;
import com.locationreview.location_review_platform.repository.ReviewLikeRepository;
import com.locationreview.location_review_platform.repository.ReviewRepository;
import com.locationreview.location_review_platform.repository.UserRepository;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.ReviewService;
import com.locationreview.location_review_platform.service.ReviewService.Input;
import java.time.LocalDate;
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
class ReviewServiceTests {
    @Mock ReviewRepository reviews;
    @Mock PlaceRepository places;
    @Mock UserRepository users;
    @Mock ReviewLikeRepository likes;
    @InjectMocks ReviewService service;

    @Test
    void invalidReviewDoesNotWriteAnything() {
        assertThatThrownBy(() -> service.create(1, 2, new Input(6, "short", LocalDate.MAX)))
                .isInstanceOfSatisfying(AccountException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.fieldErrors()).containsKeys("rating", "body", "visitDate");
                });
        verifyNoInteractions(reviews, places, users, likes);
    }

    @Test
    void createRejectsAnExistingReviewBeforeSaving() {
        when(places.findById(2L)).thenReturn(Optional.of(new Place()));
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(reviews.existsByAuthorIdAndPlaceId(1, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1, 2, new Input(4, "A helpful review", null)))
                .isInstanceOfSatisfying(AccountException.class, error -> assertThat(error.code()).isEqualTo("duplicate_review"));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void onlyTheAuthorCanDeleteAReview() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(1L);
        Review review = new Review();
        review.setAuthor(author);
        when(reviews.findById(3L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(2, 3))
                .isInstanceOfSatisfying(AccountException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(reviews, never()).delete(any());
        service.delete(1, 3);
        verify(reviews).delete(review);
        verify(reviews).flush();
    }
}
