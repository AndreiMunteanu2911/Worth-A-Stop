package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.CollectionPlace;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionPlaceRepository extends JpaRepository<CollectionPlace, Long> {
    @EntityGraph(attributePaths = "place")
    List<CollectionPlace> findByCollectionIdInAndPositionLessThanOrderByPositionAsc(List<Long> collectionIds, int position);

    @EntityGraph(attributePaths = "place")
    List<CollectionPlace> findByCollectionIdOrderByPositionAsc(long collectionId);
}
