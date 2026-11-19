package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
}
