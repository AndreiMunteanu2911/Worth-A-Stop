package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    long countByFollowedId(Long id);
    long countByFollowerId(Long id);
}
