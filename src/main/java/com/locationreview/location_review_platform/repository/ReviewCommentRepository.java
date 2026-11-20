package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.ReviewComment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<ReviewComment> findByReviewIdOrderByCreatedAtAscIdAsc(long reviewId, Pageable pageable);

    long countByReviewId(long reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewComment c where c.id = :id")
    Optional<ReviewComment> findForDeletion(long id);
}
