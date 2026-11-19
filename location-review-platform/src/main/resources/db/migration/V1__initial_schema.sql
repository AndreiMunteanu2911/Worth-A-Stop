CREATE TABLE users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(30) NOT NULL, email VARCHAR(254) NOT NULL,
 password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(80) NOT NULL,
 biography VARCHAR(500) NOT NULL DEFAULT '', created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_users_username UNIQUE (username), CONSTRAINT uk_users_email UNIQUE (email),
 INDEX ix_users_display_name (display_name, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE places (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, osm_type VARCHAR(10) NOT NULL, osm_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL, category VARCHAR(100) NOT NULL, osm_tag_value VARCHAR(100) NOT NULL,
 latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL,
 address VARCHAR(500), website VARCHAR(2048), phone VARCHAR(100), opening_hours VARCHAR(500),
 source VARCHAR(20) NOT NULL, metadata_fetched_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_places_osm UNIQUE (osm_type, osm_id),
 CONSTRAINT ck_places_osm_type CHECK (osm_type IN ('NODE','WAY','RELATION')),
 CONSTRAINT ck_places_osm_id CHECK (osm_id > 0),
 CONSTRAINT ck_places_latitude CHECK (latitude BETWEEN -90 AND 90),
 CONSTRAINT ck_places_longitude CHECK (longitude BETWEEN -180 AND 180),
 INDEX ix_places_category (category, id), INDEX ix_places_coordinates (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE reviews (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, author_id BIGINT NOT NULL, place_id BIGINT NOT NULL,
 rating TINYINT NOT NULL, body VARCHAR(5000) NOT NULL, visit_date DATE,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_reviews_author FOREIGN KEY (author_id) REFERENCES users(id),
 CONSTRAINT fk_reviews_place FOREIGN KEY (place_id) REFERENCES places(id),
 CONSTRAINT uk_reviews_author_place UNIQUE (author_id, place_id),
 CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
 INDEX ix_reviews_place_created (place_id, created_at, id), INDEX ix_reviews_author_created (author_id, created_at, id),
 INDEX ix_reviews_place_rating (place_id, rating, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE review_likes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, review_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_likes_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
 CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT uk_likes_review_user UNIQUE (review_id, user_id), INDEX ix_likes_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE review_comments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, review_id BIGINT NOT NULL, author_id BIGINT NOT NULL, body VARCHAR(1000) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_comments_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
 CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id), INDEX ix_comments_review_created (review_id, created_at, id), INDEX ix_comments_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE collections (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL,
 description VARCHAR(1000) NOT NULL, visibility VARCHAR(10) NOT NULL, published_at DATETIME(6),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_collections_owner FOREIGN KEY (owner_id) REFERENCES users(id),
 CONSTRAINT ck_collections_publication CHECK ((visibility = 'PUBLIC' AND published_at IS NOT NULL) OR (visibility = 'PRIVATE' AND published_at IS NULL)),
 INDEX ix_collections_owner_updated (owner_id, updated_at, id), INDEX ix_collections_visibility_published (visibility, published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE collection_places (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, collection_id BIGINT NOT NULL, place_id BIGINT NOT NULL, position INT NOT NULL, added_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_collection_places_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
 CONSTRAINT fk_collection_places_place FOREIGN KEY (place_id) REFERENCES places(id),
 CONSTRAINT uk_collection_places_place UNIQUE (collection_id, place_id), CONSTRAINT uk_collection_places_position UNIQUE (collection_id, position),
 CONSTRAINT ck_collection_places_position CHECK (position >= 0), INDEX ix_collection_places_order (collection_id, position, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE follows (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, follower_id BIGINT NOT NULL, followed_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id), CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users(id),
 CONSTRAINT uk_follows_pair UNIQUE (follower_id, followed_id), CONSTRAINT ck_follows_not_self CHECK (follower_id <> followed_id),
 INDEX ix_follows_followed_created (followed_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE bookmarks (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, place_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(id), CONSTRAINT fk_bookmarks_place FOREIGN KEY (place_id) REFERENCES places(id),
 CONSTRAINT uk_bookmarks_pair UNIQUE (user_id, place_id), INDEX ix_bookmarks_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
