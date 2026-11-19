package com.locationreview.location_review_platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class LocationReviewPlatformApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.repository.UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.repository.FollowRepository follows;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.PlaceService places;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.ReviewService reviews;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.ReviewInteractionService reviewInteractions;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.CollectionService collections;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.BookmarkService bookmarks;

	@Test
	void contextLoads() {
	}

}
