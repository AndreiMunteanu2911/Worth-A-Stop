package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.repository.FollowRepository;
import com.locationreview.location_review_platform.repository.FollowRepository.FeedRow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {
    public enum Kind { REVIEW_PUBLISHED, COLLECTION_PUBLISHED }

    public record Actor(long id, String username, String displayName) {}
    public record Target(long id, String url, String title, String excerpt) {}
    public record FeedItem(Kind kind, Instant eventAt, Actor actor, Target target) {}

    private final FollowRepository follows;

    public FeedService(FollowRepository follows) {
        this.follows = follows;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FeedItem> read(long viewerId, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "Page must be nonnegative with a supported offset, and size must be 1–50.", Map.of());
        }
        return PageResponse.from(follows.feed(viewerId, PageRequest.of(page, size)).map(FeedService::view));
    }

    private static FeedItem view(FeedRow row) {
        Kind kind = Kind.valueOf(row.getKind());
        long targetId = row.getTargetId();
        Target target = switch (kind) {
            case REVIEW_PUBLISHED -> new Target(targetId, "/reviews/" + targetId,
                    row.getPlaceName() == null ? "Place review" : "Review of " + row.getPlaceName(), row.getReviewExcerpt());
            case COLLECTION_PUBLISHED -> new Target(targetId, "/collections/" + targetId, row.getCollectionTitle(), null);
        };
        // The DATETIME columns store UTC. Epoch microseconds avoid JDBC's local-time conversion for native projections.
        return new FeedItem(kind, Instant.EPOCH.plus(row.getEventMicros(), ChronoUnit.MICROS),
                new Actor(row.getActorId(), row.getUsername(), row.getDisplayName()), target);
    }
}
