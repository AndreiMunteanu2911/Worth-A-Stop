package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.repository.BookmarkRepository;
import com.locationreview.location_review_platform.repository.PlaceRepository;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.BookmarkService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookmarkServiceTests {
    private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final BookmarkService service = new BookmarkService(bookmarks, places);

    @Test
    void savesAndRemovesOnlyTheCurrentUsersBookmark() {
        when(places.existsById(9L)).thenReturn(true);
        assertThat(service.save(3, 9)).isEqualTo(new BookmarkService.SavedState(true, 9));
        verify(bookmarks).saveIfAbsent(eq(3L), eq(9L), notNull());
        service.remove(3, 9);
        verify(bookmarks).remove(3, 9);
    }

    @Test
    void rejectsMissingAndInvalidTargetsWithoutWriting() {
        assertThatThrownBy(() -> service.save(3, 9)).isInstanceOfSatisfying(AccountException.class,
                error -> assertThat(error.code()).isEqualTo("place_not_found"));
        assertThatThrownBy(() -> service.remove(3, 9)).isInstanceOfSatisfying(AccountException.class,
                error -> assertThat(error.code()).isEqualTo("place_not_found"));
        assertThatThrownBy(() -> service.remove(3, 0)).isInstanceOfSatisfying(AccountException.class,
                error -> assertThat(error.code()).isEqualTo("invalid_fields"));
        verifyNoInteractions(bookmarks);
    }

    @Test
    void rejectsOutOfRangePaginationBeforeQuerying() {
        for (int[] bounds : new int[][] {{-1, 20}, {0, 0}, {0, 51}, {Integer.MAX_VALUE, 50}}) {
            assertThatThrownBy(() -> service.list(3, bounds[0], bounds[1]))
                    .isInstanceOfSatisfying(AccountException.class,
                            error -> assertThat(error.code()).isEqualTo("invalid_pagination"));
        }
        verifyNoInteractions(bookmarks);
    }
}
