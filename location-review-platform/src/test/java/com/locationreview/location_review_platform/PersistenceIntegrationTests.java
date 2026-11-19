package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;


import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
class PersistenceIntegrationTests {
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
    static { mysql.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @Test
    void migrationMappingsConstraintsAndPersistence() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(1);
        User user = new User();
        user.setUsername("reader"); user.setEmail("reader@example.com"); user.setPasswordHash("hash");
        user.setDisplayName("Reader"); user.setBiography("");
        user.setCreatedAt(Instant.parse("2026-01-01T12:00:00Z")); user.setUpdatedAt(user.getCreatedAt());
        Long userId = users.saveAndFlush(user).getId();
        assertThat(users.findById(userId).orElseThrow().getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users (username,email,password_hash,display_name,biography,created_at,updated_at) VALUES ('READER','other@example.com','x','x','',NOW(6),NOW(6))")).isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO places (osm_type,osm_id,name,category,osm_tag_value,latitude,longitude,source,metadata_fetched_at,created_at,updated_at) VALUES ('NODE',1,'Place','cafe','cafe',44.4,26.1,'OSM',NOW(6),NOW(6),NOW(6))");
        Long placeId = jdbc.queryForObject("SELECT id FROM places WHERE osm_id=1", Long.class);
        jdbc.update("INSERT INTO reviews (author_id,place_id,rating,body,created_at,updated_at) VALUES (?,?,5,'A good place',NOW(6),NOW(6))", userId, placeId);
        Long reviewId = jdbc.queryForObject("SELECT id FROM reviews WHERE author_id=?", Long.class, userId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (author_id,place_id,rating,body,created_at,updated_at) VALUES (?,?,3,'Another review',NOW(6),NOW(6))", userId, placeId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE reviews SET rating=6 WHERE id=?", reviewId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO follows (follower_id,followed_id,created_at) VALUES (?,?,NOW(6))", userId,userId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO bookmarks (user_id,place_id,created_at) VALUES (?,999999,NOW(6))", userId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO collections (owner_id,title,description,visibility,created_at,updated_at) VALUES (?,'Title','','PUBLIC',NOW(6),NOW(6))", userId)).isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO review_likes (review_id,user_id,created_at) VALUES (?,?,NOW(6))",reviewId,userId);
        jdbc.update("INSERT INTO review_comments (review_id,author_id,body,created_at,updated_at) VALUES (?,?,'Comment',NOW(6),NOW(6))",reviewId,userId);
        jdbc.update("DELETE FROM reviews WHERE id=?", reviewId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_likes",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_comments",Integer.class)).isZero();
        mysql.getDockerClient().restartContainerCmd(mysql.getContainerId()).exec();
        boolean available = false;
        for (int attempt = 0; attempt < 60 && !available; attempt++) {
            Thread.sleep(1000);
            var result = mysql.execInContainer("sh", "-c",
                    "MYSQL_PWD=\"$MYSQL_PASSWORD\" mysql -u \"$MYSQL_USER\" \"$MYSQL_DATABASE\" -N -e 'SELECT COUNT(*) FROM users'");
            available = result.getExitCode() == 0 && result.getStdout().trim().equals("1");
        }
        assertThat(available).isTrue();
    }
}



