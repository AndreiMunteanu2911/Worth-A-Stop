package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRepository extends JpaRepository<Collection, Long> {
}
