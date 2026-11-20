package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.ReviewLike;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    long countByReviewId(long reviewId);

    interface LikeSummary {
        long getReviewId();
        long getLikeCount();
        int getViewerLiked();
    }

    @Query("""
            select l.review.id as reviewId, count(l) as likeCount,
                   max(case when l.user.id = :viewerId then 1 else 0 end) as viewerLiked
            from ReviewLike l where l.review.id in :ids group by l.review.id
            """)
    List<LikeSummary> summaries(List<Long> ids, long viewerId);

    @Modifying
    @Query(value = """
            insert into review_likes (review_id, user_id, created_at)
            values (:reviewId, :userId, :createdAt)
            on duplicate key update id = id
            """, nativeQuery = true)
    void likeIfAbsent(long reviewId, long userId, Instant createdAt);

    @Modifying
    @Query("delete from ReviewLike l where l.review.id = :reviewId and l.user.id = :userId")
    void unlike(long reviewId, long userId);
}
