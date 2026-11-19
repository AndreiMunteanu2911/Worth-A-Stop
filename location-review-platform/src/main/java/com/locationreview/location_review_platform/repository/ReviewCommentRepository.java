package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
}
