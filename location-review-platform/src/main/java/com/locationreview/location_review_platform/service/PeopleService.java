package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.account.AccountDtos.UserPublic;
import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.FollowRepository;
import com.locationreview.location_review_platform.repository.FollowRepository.UserCount;
import com.locationreview.location_review_platform.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeopleService {
    public record FollowState(boolean following, long followerCount) {}
    public enum Connections { FOLLOWERS, FOLLOWING }

    private final UserRepository users;
    private final FollowRepository follows;

    public PeopleService(UserRepository users, FollowRepository follows) {
        this.users = users;
        this.follows = follows;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserPublic> search(String query, int page, int size) {
        String term = query == null ? "" : query.strip();
        int length = term.codePointCount(0, term.length());
        if (length < 2 || length > 80) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields",
                    "Enter a search term of 2–80 characters.", Map.of("q", "must have 2–80 characters"));
        }
        return publicPage(users.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByUsernameAscIdAsc(
                term, term, pagination(page, size)));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserPublic> connections(long userId, Connections direction, int page, int size) {
        requireUser(userId);
        var pageable = pagination(page, size);
        return publicPage(direction == Connections.FOLLOWERS
                ? follows.followers(userId, pageable) : follows.following(userId, pageable));
    }

    public boolean isFollowing(long viewerId, long userId) {
        return follows.existsByFollowerIdAndFollowedId(viewerId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FollowState follow(long viewerId, long userId) {
        requireTarget(viewerId, userId);
        follows.followIfAbsent(viewerId, userId, Instant.now());
        return new FollowState(true, follows.countByFollowedId(userId));
    }

    @Transactional
    public void unfollow(long viewerId, long userId) {
        requireTarget(viewerId, userId);
        follows.unfollow(viewerId, userId);
    }

    private PageResponse<UserPublic> publicPage(Page<User> page) {
        if (page.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
        List<Long> ids = page.getContent().stream().map(User::getId).toList();
        Map<Long, Long> followers = counts(follows.followerCounts(ids));
        Map<Long, Long> following = counts(follows.followingCounts(ids));
        return PageResponse.from(page.map(user -> UserPublic.from(user,
                followers.getOrDefault(user.getId(), 0L), following.getOrDefault(user.getId(), 0L))));
    }

    private static Map<Long, Long> counts(List<UserCount> counts) {
        return counts.stream().collect(Collectors.toMap(UserCount::getUserId, UserCount::getTotal));
    }

    private void requireTarget(long viewerId, long userId) {
        requireUser(userId);
        if (viewerId == userId) {
            throw new AccountException(HttpStatus.CONFLICT, "self_follow", "You cannot follow yourself.", Map.of());
        }
    }

    private void requireUser(long userId) {
        if (userId < 1) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields",
                    "Check the user ID.", Map.of("id", "must be greater than 0"));
        }
        if (!users.existsById(userId)) {
            throw new AccountException(HttpStatus.NOT_FOUND, "user_not_found", "User not found.", Map.of());
        }
    }

    private static PageRequest pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "Page must be nonnegative with a supported offset, and size must be 1–50.", Map.of());
        }
        return PageRequest.of(page, size);
    }
}
