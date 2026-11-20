-- Fictional community content for local development. Run against the initialized app database.
SET @seed_lock_name = CONCAT(DATABASE(), '.location_review_sample_seed');
SET @seed_lock = GET_LOCK(@seed_lock_name, 10);
START TRANSACTION;

-- The app has no account deletion flow, so this fixture account acts as the seed marker.
SET @seed = @seed_lock = 1 AND NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'sample_mara' AND email = 'mara@location-review-sample.invalid'
);
SET @seed_time = UTC_TIMESTAMP(6);
-- BCrypt of a discarded random value. These fixture accounts have no known password.
SET @sample_hash = '$2b$12$8syAeYScVwHx1egoPzc.LOV4Pb0PGplTq/FqL3sN7UrRXaYsk4JZS';

INSERT INTO users (username, email, password_hash, display_name, biography, created_at, updated_at)
SELECT username, CONCAT(email_name, '@location-review-sample.invalid'), @sample_hash, display_name,
       biography, DATE_SUB(@seed_time, INTERVAL 30 DAY), DATE_SUB(@seed_time, INTERVAL 30 DAY)
FROM (VALUES
    ROW('sample_mara', 'mara', 'Mara (sample)', 'Fictional profile that collects walking routes and quiet parks.'),
    ROW('sample_daria', 'daria', 'Daria (sample)', 'Fictional profile interested in museums and music.'),
    ROW('sample_alex', 'alex', 'Alex (sample)', 'Fictional profile collecting architecture and outdoor stops.'),
    ROW('sample_radu', 'radu', 'Radu (sample)', 'Fictional profile planning short weekend visits.')
) AS sample_users(username, email_name, display_name, biography)
WHERE @seed;

-- Real Bucharest place references. Map data © OpenStreetMap contributors, https://www.openstreetmap.org/copyright.
INSERT INTO places (osm_type, osm_id, name, category, osm_tag_value, latitude, longitude, source,
                    metadata_fetched_at, created_at, updated_at)
SELECT 'WAY', sample_places.osm_id, name, category, tag, latitude, longitude, 'OSM', @seed_time, @seed_time, @seed_time
FROM (VALUES
    ROW(16291869, 'Cișmigiu Gardens', 'PARK', 'park', 44.4370000, 26.0907306),
    ROW(16291602, 'Romanian Athenaeum', 'LANDMARK', 'theatre', 44.4413056, 26.0973056),
    ROW(23610985, 'Grigore Antipa Natural History Museum', 'MUSEUM', 'museum', 44.4531000, 26.0846000),
    ROW(217851530, 'Arcul de Triumf', 'LANDMARK', 'monument', 44.4672778, 26.0783611)
) AS sample_places(osm_id, name, category, tag, latitude, longitude)
WHERE @seed AND NOT EXISTS (
    SELECT 1 FROM places p WHERE p.osm_type = 'WAY' AND p.osm_id = sample_places.osm_id
);

SET @mara = (SELECT id FROM users WHERE username = 'sample_mara');
SET @daria = (SELECT id FROM users WHERE username = 'sample_daria');
SET @alex = (SELECT id FROM users WHERE username = 'sample_alex');
SET @radu = (SELECT id FROM users WHERE username = 'sample_radu');
SET @gardens = (SELECT id FROM places WHERE osm_type = 'WAY' AND osm_id = 16291869);
SET @athenaeum = (SELECT id FROM places WHERE osm_type = 'WAY' AND osm_id = 16291602);
SET @museum = (SELECT id FROM places WHERE osm_type = 'WAY' AND osm_id = 23610985);
SET @arch = (SELECT id FROM places WHERE osm_type = 'WAY' AND osm_id = 217851530);

INSERT INTO reviews (author_id, place_id, rating, body, visit_date, created_at, updated_at)
SELECT author_id, place_id, rating, body, DATE(DATE_SUB(@seed_time, INTERVAL days_ago DAY)),
       DATE_SUB(@seed_time, INTERVAL days_ago DAY), DATE_SUB(@seed_time, INTERVAL days_ago DAY)
FROM (VALUES
    ROW(@mara, @gardens, 5, 'A short loop around the lake was the easiest way to slow down after a busy morning.', 7),
    ROW(@daria, @gardens, 4, 'I liked the side paths for a quiet walk and left the main loop for the way back.', 14),
    ROW(@radu, @gardens, 4, 'This made a good short stop between longer walks on the same afternoon.', 19),
    ROW(@daria, @athenaeum, 5, 'I left extra time to look at the exterior before the evening concert.', 8),
    ROW(@alex, @athenaeum, 5, 'The view from across the square made the shape of the building easier to take in.', 12),
    ROW(@mara, @athenaeum, 4, 'A compact stop that worked well alongside a longer walk through the center.', 17),
    ROW(@alex, @museum, 4, 'I set aside most of the afternoon and took my time with the natural history displays.', 9),
    ROW(@radu, @museum, 5, 'My favorite part was comparing the displays instead of rushing through the galleries.', 13),
    ROW(@mara, @museum, 3, 'There was more to see than I could fit into a quick visit. I would allow more time next time.', 21),
    ROW(@radu, @arch, 4, 'Easy to include as an outdoor stop on a longer architecture walk.', 10),
    ROW(@alex, @arch, 5, 'I enjoyed seeing the monument from a few different angles during the same walk.', 15),
    ROW(@daria, @arch, 3, 'Worth seeing in passing, especially when paired with another stop nearby.', 22)
) AS sample_reviews(author_id, place_id, rating, body, days_ago)
WHERE @seed;

INSERT INTO collections (owner_id, title, description, visibility, published_at, created_at, updated_at)
SELECT owner_id, title, description, visibility,
       CASE WHEN visibility = 'PUBLIC' THEN DATE_SUB(@seed_time, INTERVAL days_ago DAY) END,
       DATE_SUB(@seed_time, INTERVAL days_ago DAY), DATE_SUB(@seed_time, INTERVAL days_ago DAY)
FROM (VALUES
    ROW(@mara, 'Sample: An afternoon in Bucharest', 'Example route for a short walk and an architecture stop.', 'PUBLIC', 4),
    ROW(@daria, 'Sample: Culture stops', 'Example collection pairing music and natural history.', 'PUBLIC', 5),
    ROW(@alex, 'Sample: North Bucharest landmarks', 'Example route through two stops in northern Bucharest.', 'PUBLIC', 6),
    ROW(@radu, 'Sample: Weekend shortlist', 'Fictional private planning list.', 'PRIVATE', 3)
) AS sample_collections(owner_id, title, description, visibility, days_ago)
WHERE @seed;

INSERT INTO collection_places (collection_id, place_id, position, added_at)
SELECT c.id, item.place_id, item.position, c.created_at
FROM (VALUES
    ROW(@mara, @gardens, 0), ROW(@mara, @athenaeum, 1),
    ROW(@daria, @athenaeum, 0), ROW(@daria, @museum, 1),
    ROW(@alex, @museum, 0), ROW(@alex, @arch, 1),
    ROW(@radu, @gardens, 0), ROW(@radu, @arch, 1)
) AS item(owner_id, place_id, position)
JOIN collections c ON c.owner_id = item.owner_id
WHERE @seed;

INSERT INTO follows (follower_id, followed_id, created_at)
SELECT follower_id, followed_id, DATE_SUB(@seed_time, INTERVAL 25 DAY)
FROM (VALUES
    ROW(@mara, @daria), ROW(@mara, @alex), ROW(@daria, @mara),
    ROW(@alex, @radu), ROW(@alex, @daria), ROW(@radu, @mara)
) AS sample_follows(follower_id, followed_id)
WHERE @seed;

INSERT INTO review_likes (review_id, user_id, created_at)
SELECT r.id, reaction.reader_id, DATE_SUB(@seed_time, INTERVAL 2 DAY)
FROM (VALUES
    ROW(@mara, @gardens, @daria), ROW(@mara, @gardens, @alex), ROW(@daria, @athenaeum, @mara),
    ROW(@daria, @athenaeum, @radu), ROW(@alex, @museum, @daria), ROW(@radu, @arch, @alex)
) AS reaction(author_id, place_id, reader_id)
JOIN reviews r ON r.author_id = reaction.author_id AND r.place_id = reaction.place_id
WHERE @seed;

INSERT INTO review_comments (review_id, author_id, body, created_at, updated_at)
SELECT r.id, comment_entry.reader_id, comment_entry.body, DATE_SUB(@seed_time, INTERVAL 1 DAY), DATE_SUB(@seed_time, INTERVAL 1 DAY)
FROM (VALUES
    ROW(@mara, @gardens, @daria, 'Adding the lake loop to my next walking route.'),
    ROW(@mara, @gardens, @alex, 'I paired this stop with a walk past the Athenaeum.'),
    ROW(@daria, @athenaeum, @mara, 'I like giving myself time outside before a concert too.'),
    ROW(@alex, @museum, @radu, 'An afternoon sounds like a better plan than my quick visit.')
) AS comment_entry(author_id, place_id, reader_id, body)
JOIN reviews r ON r.author_id = comment_entry.author_id AND r.place_id = comment_entry.place_id
WHERE @seed;

INSERT INTO bookmarks (user_id, place_id, created_at)
SELECT user_id, place_id, DATE_SUB(@seed_time, INTERVAL 3 DAY)
FROM (VALUES
    ROW(@mara, @arch), ROW(@mara, @museum), ROW(@daria, @gardens),
    ROW(@alex, @athenaeum), ROW(@radu, @museum), ROW(@radu, @gardens)
) AS sample_bookmarks(user_id, place_id)
WHERE @seed;

COMMIT;
DO RELEASE_LOCK(@seed_lock_name);

SELECT CASE
    WHEN COALESCE(@seed_lock, 0) <> 1 THEN 'Another sample import is running. Try again.'
    WHEN @seed THEN 'Sample data added.'
    ELSE 'Sample data is already present.'
END AS result;
