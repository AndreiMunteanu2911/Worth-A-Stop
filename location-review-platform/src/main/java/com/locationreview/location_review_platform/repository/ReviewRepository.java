package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @EntityGraph(attributePaths = {"author", "place"})
    Page<Review> findByPlaceId(long placeId, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "place"})
    Page<Review> findByAuthorId(long authorId, Pageable pageable);
    boolean existsByAuthorIdAndPlaceId(long authorId, long placeId);
    interface RatingBucket { Byte getRating(); Long getCount(); }
    @Query("select r.rating as rating, count(r) as count from Review r where r.place.id = :placeId group by r.rating")
    List<RatingBucket> distribution(@Param("placeId") long placeId);
    interface RatingAggregate { Long getCount(); Double getAverage(); }
    @Query("select count(r) as count, avg(r.rating) as average from Review r where r.place.id = :placeId")
    RatingAggregate ratingForPlace(@Param("placeId") long placeId);
}
