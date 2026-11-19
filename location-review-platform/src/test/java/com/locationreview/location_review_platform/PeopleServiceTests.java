package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.FollowRepository;
import com.locationreview.location_review_platform.repository.UserRepository;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.PeopleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import static com.locationreview.location_review_platform.service.PeopleService.Connections.FOLLOWERS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeopleServiceTests {
    @Mock UserRepository users;
    @Mock FollowRepository follows;
    @InjectMocks PeopleService people;

    @Test
    void rejectsInvalidSearchBeforeReadingTheDatabase() {
        assertThatThrownBy(() -> people.search(null, 0, 20))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.fieldErrors()).containsKey("q"));
        assertThatThrownBy(() -> people.search("valid", Integer.MAX_VALUE, 50))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("invalid_pagination"));
        verifyNoInteractions(users, follows);
    }

    @Test
    void mapsPublicProfilesWithBatchedCountsForTheCurrentPage() {
        User alice = mock(User.class);
        when(alice.getId()).thenReturn(4L);
        when(alice.getUsername()).thenReturn("alice");
        when(alice.getDisplayName()).thenReturn("Alice");
        when(alice.getBiography()).thenReturn("Reader");
        var page = PageRequest.of(0, 1);
        when(users.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByUsernameAscIdAsc("Ali", "Ali", page))
                .thenReturn(new PageImpl<>(List.of(alice), page, 2));
        FollowRepository.UserCount count = mock(FollowRepository.UserCount.class);
        when(count.getUserId()).thenReturn(4L);
        when(count.getTotal()).thenReturn(2L);
        when(follows.followerCounts(List.of(4L))).thenReturn(List.of(count));
        when(follows.followingCounts(List.of(4L))).thenReturn(List.of());

        var result = people.search(" Ali ", 0, 1);

        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.items()).singleElement().satisfies(person -> {
            assertThat(person.username()).isEqualTo("alice");
            assertThat(person.avatarPlaceholder()).isEqualTo("A");
            assertThat(person.followerCount()).isEqualTo(2);
            assertThat(person.followingCount()).isZero();
        });
        verify(follows).followerCounts(List.of(4L));
        verify(follows).followingCounts(List.of(4L));
        verifyNoMoreInteractions(follows);
    }

    @Test
    void missingTargetsAndSelfActionsDoNotMutateRelationships() {
        when(users.existsById(2L)).thenReturn(true);
        when(users.existsById(3L)).thenReturn(false);
        assertThatThrownBy(() -> people.connections(3L, FOLLOWERS, 0, 20))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> people.follow(2L, 2L))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("self_follow"));
        assertThatThrownBy(() -> people.unfollow(2L, 2L))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.code()).isEqualTo("self_follow"));
        verifyNoInteractions(follows);
    }

    @Test
    void followWritesBeforeCountingAndUnfollowUsesTheSamePair() {
        when(users.existsById(2L)).thenReturn(true);
        when(follows.countByFollowedId(2L)).thenReturn(3L);
        when(follows.existsByFollowerIdAndFollowedId(1L, 2L)).thenReturn(true);
        assertThat(people.follow(1L, 2L)).isEqualTo(new PeopleService.FollowState(true, 3L));
        assertThat(people.isFollowing(1L, 2L)).isTrue();
        people.unfollow(1L, 2L);
        var order = inOrder(follows);
        order.verify(follows).followIfAbsent(eq(1L), eq(2L), any(Instant.class));
        order.verify(follows).countByFollowedId(2L);
        order.verify(follows).existsByFollowerIdAndFollowedId(1L, 2L);
        order.verify(follows).unfollow(1L, 2L);
    }
}
