package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
