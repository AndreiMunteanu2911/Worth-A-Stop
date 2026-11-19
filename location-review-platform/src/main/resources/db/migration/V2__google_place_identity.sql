-- Preserve OSM identities and all dependent user content; Google rows retain IDs only.
ALTER TABLE places
 MODIFY osm_type VARCHAR(10) NULL,
 MODIFY osm_id BIGINT NULL,
 MODIFY name VARCHAR(255) NULL,
 MODIFY category VARCHAR(100) NULL,
 MODIFY osm_tag_value VARCHAR(100) NULL,
 MODIFY latitude DECIMAL(10,7) NULL,
 MODIFY longitude DECIMAL(10,7) NULL,
 MODIFY metadata_fetched_at DATETIME(6) NULL,
 ADD google_place_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT uk_places_google UNIQUE (google_place_id),
 ADD CONSTRAINT ck_places_identity CHECK (
   (source = 'GOOGLE' AND google_place_id IS NOT NULL AND osm_type IS NULL AND osm_id IS NULL
     AND name IS NULL AND category IS NULL AND osm_tag_value IS NULL
     AND latitude IS NULL AND longitude IS NULL AND address IS NULL
     AND website IS NULL AND phone IS NULL AND opening_hours IS NULL AND metadata_fetched_at IS NULL)
   OR (source = 'OSM' AND google_place_id IS NULL AND osm_type IS NOT NULL AND osm_id IS NOT NULL)
 );
