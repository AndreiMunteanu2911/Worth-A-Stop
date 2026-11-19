package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.repository.FollowRepository;
import com.locationreview.location_review_platform.repository.FollowRepository.FeedRow;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.FeedService;
import com.locationreview.location_review_platform.service.FeedService.Kind;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTests {
    @Mock FollowRepository follows;
    @InjectMocks FeedService feed;

    @Test
    void boundsPaginationBeforeQuerying() {
        for (int[] input : new int[][]{{-1, 20}, {0, 0}, {0, 51}, {Integer.MAX_VALUE, 2}}) {
            assertThatThrownBy(() -> feed.read(12, input[0], input[1]))
                    .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("invalid_pagination"));
        }
        verifyNoInteractions(follows);
    }

    @Test
    void mapsOnlyTheRepositoryPageToPublicActivity() {
        var page = PageRequest.of(1, 2);
        var review = mock(FeedRow.class);
        when(review.getKind()).thenReturn("REVIEW_PUBLISHED");
        when(review.getTargetId()).thenReturn(7L);
        var eventAt = Instant.parse("2026-06-01T12:00:00.123456Z");
        when(review.getEventMicros()).thenReturn(ChronoUnit.MICROS.between(Instant.EPOCH, eventAt));
        when(review.getActorId()).thenReturn(9L);
        when(review.getUsername()).thenReturn("author");
        when(review.getDisplayName()).thenReturn("Author");
        when(review.getReviewExcerpt()).thenReturn("Review text");
        var collection = mock(FeedRow.class);
        when(collection.getKind()).thenReturn("COLLECTION_PUBLISHED");
        when(collection.getTargetId()).thenReturn(8L);
        when(collection.getCollectionTitle()).thenReturn("Day trip");
        when(follows.feed(12L, page)).thenReturn(new PageImpl<>(List.of(review, collection), page, 7L));

        var result = feed.read(12, 1, 2);

        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.totalPages()).isEqualTo(4);
        assertThat(result.items()).extracting(FeedService.FeedItem::kind)
                .containsExactly(Kind.REVIEW_PUBLISHED, Kind.COLLECTION_PUBLISHED);
        var item = result.items().getFirst();
        assertThat(item.actor()).isEqualTo(new FeedService.Actor(9, "author", "Author"));
        assertThat(item.eventAt()).isEqualTo(eventAt);
        assertThat(item.target()).isEqualTo(new FeedService.Target(7, "/reviews/7", "Place review", "Review text"));
        assertThat(result.items().getLast().target()).isEqualTo(new FeedService.Target(8, "/collections/8", "Day trip", null));
        verify(follows).feed(12L, page);
        verifyNoMoreInteractions(follows);
    }
}
