package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
}
