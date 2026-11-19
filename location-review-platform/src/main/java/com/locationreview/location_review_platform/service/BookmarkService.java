package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.entity.Place;
import com.locationreview.location_review_platform.repository.BookmarkRepository;
import com.locationreview.location_review_platform.repository.PlaceRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {
    private final BookmarkRepository bookmarks;
    private final PlaceRepository places;

    public record SavedState(boolean saved, long placeId) {}
    public record BookmarkView(PlaceSummary place, Instant createdAt) {}
    public record PlaceSummary(long id, String name, String category, String address, String source) {
        private PlaceSummary(Place place) {
            this(place.getId(), place.getName(), place.getCategory(), place.getAddress(), place.getSource());
        }
    }

    public BookmarkService(BookmarkRepository bookmarks, PlaceRepository places) {
        this.bookmarks = bookmarks;
        this.places = places;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookmarkView> list(long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "Page must be nonnegative and size must be 1–50.", Map.of());
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "The requested page offset is too large.", Map.of());
        }
        return PageResponse.from(bookmarks.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size))
                .map(bookmark -> new BookmarkView(new PlaceSummary(bookmark.getPlace()), bookmark.getCreatedAt())));
    }

    public boolean isSaved(long userId, long placeId) {
        return bookmarks.existsByUserIdAndPlaceId(userId, placeId);
    }

    public boolean isGooglePlaceSaved(long userId, String googlePlaceId) {
        return bookmarks.existsByUserIdAndPlaceGooglePlaceId(userId, googlePlaceId);
    }

    @Transactional
    public SavedState save(long userId, long placeId) {
        requirePlace(placeId);
        bookmarks.saveIfAbsent(userId, placeId, Instant.now());
        return new SavedState(true, placeId);
    }

    @Transactional
    public void remove(long userId, long placeId) {
        requirePlace(placeId);
        bookmarks.remove(userId, placeId);
    }

    private void requirePlace(long placeId) {
        if (placeId < 1) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields",
                    "Check the place ID.", Map.of("placeId", "must be greater than 0"));
        }
        if (!places.existsById(placeId)) {
            throw new AccountException(HttpStatus.NOT_FOUND, "place_not_found", "Place not found.", Map.of());
        }
    }
}
