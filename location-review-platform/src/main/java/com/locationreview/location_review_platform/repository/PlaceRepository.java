package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Place;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByGooglePlaceId(String googlePlaceId);
    @Query("select p from Place p where p.id <> :id and p.googlePlaceId is not null and exists (select r.id from Review r where r.place = p) order by p.id")
    List<Place> reviewedCandidates(long id, Pageable pageable);
}
