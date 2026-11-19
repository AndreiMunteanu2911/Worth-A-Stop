package com.locationreview.location_review_platform.repository;

import com.locationreview.location_review_platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
