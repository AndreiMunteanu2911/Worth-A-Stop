package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    interface RatingAggregate { Long getCount(); Double getAverage(); }
    @Query("select count(r) as count, avg(r.rating) as average from Review r where r.place.id = :placeId")
    RatingAggregate ratingForPlace(@Param("placeId") long placeId);
}
