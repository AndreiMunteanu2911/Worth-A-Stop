# Database design

The application uses MySQL 8.4. Flyway applies `V1__initial_schema.sql` and then `V2__google_place_identity.sql`. V1 starts from an empty schema, and Flyway does not automatically baseline a populated database. Hibernate validates the resulting schema at startup. IDs are generated signed `BIGINT`s. Timestamps are `DATETIME(6)` interpreted as UTC by JDBC and Hibernate. Review visit dates use `DATE`. Tables use InnoDB and `utf8mb4_0900_ai_ci`, with a binary ASCII collation for Google place IDs.

| Table | Stored data and database constraints |
| --- | --- |
| `users` | Username and email are individually unique under the case-insensitive collation; password hash, display name, biography and timestamps. |
| `places` | Unique, case-sensitive Google ID for new identities. Legacy rows keep a unique OSM type and positive ID, with their preserved metadata. The source constraint excludes provider metadata from stored Google rows. |
| `reviews` | Author and place foreign keys, unique author/place pair, rating 1–5, body, optional visit date and timestamps. |
| `review_likes` | Review and user foreign keys, unique review/user pair and creation time. |
| `review_comments` | Review and author foreign keys, body and timestamps. |
| `collections` | Owner, title, description, visibility and timestamps. PUBLIC requires a publication time; PRIVATE requires it to be null. |
| `collection_places` | Collection and place foreign keys; unique place and unique nonnegative position within each collection; added time. |
| `follows` | Follower and followed user foreign keys, unique pair, creation time and a check prohibiting self-follow. |
| `bookmarks` | User and place foreign keys, unique pair and creation time. |

Deleting a review cascades to its likes and comments; deleting a collection cascades to its membership rows. Other relationships restrict deletion. Indexes support the owner, place, time, rating, visibility, ordering and follower access paths in the migration. Review validation and lifecycle rules are enforced by the review service. CollectionService serializes mutations with a collection row lock, enforces the 100-place limit and dense 0-based ordering, and uses temporary positive positions during reorder to avoid transient unique-key collisions.

```mermaid
erDiagram
  USER ||--o{ REVIEW : authors
  PLACE ||--o{ REVIEW : receives
  USER ||--o{ COLLECTION : owns
  COLLECTION ||--o{ COLLECTION_PLACE : contains
  PLACE ||--o{ COLLECTION_PLACE : appears_in
  USER ||--o{ BOOKMARK : saves
  PLACE ||--o{ BOOKMARK : saved
  USER ||--o{ REVIEW_LIKE : creates
  REVIEW ||--o{ REVIEW_LIKE : has
  USER ||--o{ REVIEW_COMMENT : authors
  REVIEW ||--o{ REVIEW_COMMENT : has
  USER ||--o{ FOLLOW : follower
  USER ||--o{ FOLLOW : followed
```

The feed derives activity from `reviews.created_at` and currently public `collections.published_at`, filtered through `follows`. Review author and collection owner indexes support this lookup. The feed requires no activity table. Account passwords are stored as BCrypt hashes.

Bookmarks use a unique user/place pair and the `(user_id, created_at, id)` index for newest-first pagination. A MySQL upsert preserves the original row and timestamp on repeated or concurrent saves. Removal is scoped to the signed-in user's pair. Both operations leave the underlying place and collection memberships intact. See [Saved places](bookmarks.md).

## Google identity migration

V2__google_place_identity.sql adds nullable `google_place_id VARCHAR(255)` with an ASCII binary collation and a unique constraint. It makes legacy OSM identity/metadata columns nullable. A check permits either the existing OSM identity or a GOOGLE identity with all provider metadata columns null. New Google identities retain only their provider ID, source and application timestamps; metadata is fetched at read time. Existing OSM records and foreign keys from reviews, bookmarks and collection memberships are retained. V1 is unchanged. The Java 21 automated suite applied V1/V2 to isolated MySQL 8.4 databases and validated JPA mappings, the Google metadata constraint, concurrent identity resolution and persistence. These checks do not alter the developer database.
