# Database design

The application uses MySQL 8.4 and Flyway migration `V1__initial_schema.sql`. Hibernate validates the schema at startup; it does not create or update tables. The migration is intended for an empty schema and does not automatically baseline a populated database. IDs are generated signed `BIGINT`s. Timestamps are `DATETIME(6)` interpreted as UTC by the JDBC session and Hibernate; review visit dates are `DATE`. Tables use InnoDB and `utf8mb4_0900_ai_ci`.

| Table | Stored data and database constraints |
| --- | --- |
| `users` | Username and email are individually unique under the case-insensitive collation; password hash, display name, biography and timestamps. |
| `places` | Unique OSM type and positive ID; name, category, tag, bounded coordinates, optional address/contact fields, source and metadata timestamps. Type is NODE, WAY or RELATION. |
| `reviews` | Author and place foreign keys, unique author/place pair, rating 1–5, body, optional visit date and timestamps. |
| `review_likes` | Review and user foreign keys, unique review/user pair and creation time. |
| `review_comments` | Review and author foreign keys, body and timestamps. |
| `collections` | Owner, title, description, visibility and timestamps. PUBLIC requires a publication time; PRIVATE requires it to be null. |
| `collection_places` | Collection and place foreign keys; unique place and unique nonnegative position within each collection; added time. |
| `follows` | Follower and followed user foreign keys, unique pair, creation time and a check prohibiting self-follow. |
| `bookmarks` | User and place foreign keys, unique pair and creation time. |

Deleting a review cascades to its likes and comments; deleting a collection cascades to its membership rows. Other relationships restrict deletion. Indexes support the owner, place, time, rating, visibility, ordering and follower access paths in the migration. Additional business rules such as dense collection ordering and validated review text belong to future service layers; they are not yet implemented as application workflows.

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

Accounts use the users table for registration, authentication and profile editing. Place discovery, reviews, collections and social workflows remain to be implemented.
