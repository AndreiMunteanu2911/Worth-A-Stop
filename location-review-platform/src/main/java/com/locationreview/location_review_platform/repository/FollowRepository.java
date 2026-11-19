package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.Follow;
import com.locationreview.location_review_platform.entity.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    long countByFollowedId(Long id);
    long countByFollowerId(Long id);

    boolean existsByFollowerIdAndFollowedId(long followerId, long followedId);

    @Query(value = """
            select u from Follow f join f.follower u where f.followed.id = :id
            order by f.createdAt desc, f.id desc
            """, countQuery = "select count(f) from Follow f where f.followed.id = :id")
    Page<User> followers(long id, Pageable pageable);

    @Query(value = """
            select u from Follow f join f.followed u where f.follower.id = :id
            order by f.createdAt desc, f.id desc
            """, countQuery = "select count(f) from Follow f where f.follower.id = :id")
    Page<User> following(long id, Pageable pageable);

    interface UserCount {
        Long getUserId();
        long getTotal();
    }

    @Query("select f.followed.id as userId, count(f) as total from Follow f where f.followed.id in :ids group by f.followed.id")
    List<UserCount> followerCounts(List<Long> ids);

    @Query("select f.follower.id as userId, count(f) as total from Follow f where f.follower.id in :ids group by f.follower.id")
    List<UserCount> followingCounts(List<Long> ids);

    @Modifying
    @Query(value = """
            insert into follows (follower_id, followed_id, created_at)
            values (:followerId, :followedId, :createdAt)
            on duplicate key update id = id
            """, nativeQuery = true)
    void followIfAbsent(long followerId, long followedId, Instant createdAt);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :followerId and f.followed.id = :followedId")
    void unfollow(long followerId, long followedId);
}
