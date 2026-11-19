package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Collection;
import com.locationreview.location_review_platform.entity.Visibility;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, Long> {
    @EntityGraph(attributePaths = "owner")
    Page<Collection> findByVisibility(Visibility visibility, Pageable page);
    @EntityGraph(attributePaths = "owner")
    Page<Collection> findByOwnerId(long ownerId, Pageable page);
    @EntityGraph(attributePaths = "owner")
    Page<Collection> findByOwnerIdAndVisibility(long ownerId, Visibility visibility, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Collection c where c.id = :id")
    Optional<Collection> lockById(@Param("id") long id);
}
