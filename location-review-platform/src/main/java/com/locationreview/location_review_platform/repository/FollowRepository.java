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
    String FOLLOWED_REVIEWS = """
            from follows f join reviews r on r.author_id = f.followed_id
            where f.follower_id = :viewerId
            """;
    String FOLLOWED_PUBLIC_COLLECTIONS = """
            from follows f join collections c on c.owner_id = f.followed_id
            where f.follower_id = :viewerId and c.visibility = 'PUBLIC' and c.published_at is not null
            """;

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

    interface FeedRow {
        String getKind();
        long getTargetId();
        long getEventMicros();
        long getActorId();
        String getUsername();
        String getDisplayName();
        String getPlaceName();
        String getReviewExcerpt();
        String getCollectionTitle();
    }

    @Query(value = """
            select events.kind, events.targetId,
                   timestampdiff(microsecond, '1970-01-01 00:00:00', events.eventAt) as eventMicros,
                   u.id as actorId, u.username, u.display_name as displayName,
                   p.name as placeName, c.title as collectionTitle,
                   case when char_length(r.body) > 240 then concat(left(r.body, 240), '…') else r.body end as reviewExcerpt
            from (
                select 'REVIEW_PUBLISHED' as kind, r.id as targetId, r.author_id as actorId, r.created_at as eventAt
            """ + FOLLOWED_REVIEWS + """
                union all
                select 'COLLECTION_PUBLISHED', c.id, c.owner_id, c.published_at
            """ + FOLLOWED_PUBLIC_COLLECTIONS + """
            ) events
            join users u on u.id = events.actorId
            left join reviews r on events.kind = 'REVIEW_PUBLISHED' and r.id = events.targetId
            left join places p on p.id = r.place_id
            left join collections c on events.kind = 'COLLECTION_PUBLISHED' and c.id = events.targetId
            order by events.eventAt desc, events.kind asc, events.targetId desc
            """, countQuery = "select (select count(*) " + FOLLOWED_REVIEWS + ") + (select count(*) "
                    + FOLLOWED_PUBLIC_COLLECTIONS + ")", nativeQuery = true)
    Page<FeedRow> feed(long viewerId, Pageable pageable);

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
