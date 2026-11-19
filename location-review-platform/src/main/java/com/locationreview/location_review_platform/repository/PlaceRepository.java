package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
