package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Bookmark;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    @EntityGraph(attributePaths = "place")
    Page<Bookmark> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);

    boolean existsByUserIdAndPlaceId(long userId, long placeId);

    boolean existsByUserIdAndPlaceGooglePlaceId(long userId, String googlePlaceId);

    // The unique key handles retries and concurrent saves without replacing the original timestamp.
    @Modifying
    @Query(value = """
            INSERT INTO bookmarks (user_id, place_id, created_at)
            VALUES (:userId, :placeId, :createdAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void saveIfAbsent(long userId, long placeId, Instant createdAt);

    @Modifying
    @Query("delete from Bookmark b where b.user.id = :userId and b.place.id = :placeId")
    void remove(long userId, long placeId);
}
