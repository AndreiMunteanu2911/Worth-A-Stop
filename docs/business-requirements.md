# Business requirements — Location Review Platform

**Document status:** approved requirements baseline; application implementation and verification are separate. These requirements describe the intended system and do not assert that the features are already available.

## System definition

Location Review Platform is a consumer web application for discovering real OpenStreetMap places and sharing locally authored ratings, reviews, curated collections, bookmarks, and social activity. There is one normal registered user type. Guests may discover places and public content; registered users can publish and manage their own content. The platform's ratings are derived exclusively from its own reviews, not from map data.

Each requirement below has a stable identifier, an MVP feature mapping, and observable acceptance criteria. The five feature descriptions are in [MVP features](mvp-features.md). `All` denotes a requirement applying across all five features.

## Requirements and acceptance criteria

| ID | Feature | Requirement and acceptance criteria |
| --- | --- | --- |
| BR-01 | MVP-01 | **Registration and authentication.** A visitor registers with a unique username and email, valid fields, and a password stored only as a secure hash; can log in and log out; protected routes reject anonymous changes. Duplicate and invalid submissions show useful errors. |
| BR-02 | MVP-01 | **Profiles.** Public profile displays username, display name, biography, avatar initial/placeholder, reviews, public collections, and relevant activity. Owner can edit username, email, display name, and biography; a public profile never exposes email, password hash, or private content. Uploaded avatars are a deferred optional enhancement. |
| BR-03 | MVP-02 | **Geographical discovery.** Map pans and zooms, shows real OSM places from a bounded area, opens marker summaries and detail pages, and offers a usable list fallback. Search accepts place name and area. |
| BR-04 | MVP-02 | **Search and filtering.** Search results can be filtered by supported category, proximity, and platform rating where data exists; sorting is available where meaningful. Results are bounded and explain empty or unavailable states. |
| BR-05 | MVP-02 | **Place details.** A place page shows available OSM identity, name, category, coordinates/address, map, own-platform rating statistics and reviews, and relevant recommendations. Missing external fields are omitted gracefully; source attribution is visible. |
| BR-06 | MVP-03 | **Publish reviews.** An authenticated user can submit an integer 1–5 rating, written content, and optional nonfuture visit date for a server-verified OSM-linked place. Exactly one current review per author/place is allowed. The persisted review names the signed-in author; invalid, duplicate, or nonexistent targets are rejected. |
| BR-07 | MVP-03 | **Manage reviews.** Authors can edit/delete their reviews; other users cannot. Changed/deleted reviews immediately affect displayed aggregates and lists. Deleting a review also removes its likes/comments and feed presence. |
| BR-08 | MVP-03 | **Ratings.** A place's average, count, and any distribution use local reviews only. Empty places show no invented rating. Create/edit/delete paths yield consistent aggregates, including zero-review state. |
| BR-09 | MVP-04 | **Collections.** A signed-in user can create an empty or populated collection with title, description, PUBLIC/PRIVATE visibility, and real resolved places. Public collections are discoverable; private ones are visible only to their owner. |
| BR-10 | MVP-04 | **Collection management.** Owner can edit metadata, add/remove/reorder places, and delete the collection. Duplicate place entries are rejected; unauthorized edits are denied. Ordering is stable and survives restart. |
| BR-11 | MVP-05 | **Following.** A user can follow/unfollow another user, not self, and cannot create a duplicate follow. Profile follower/following counts reflect the persisted relationship. |
| BR-12 | MVP-05 | **Feed.** A signed-in user sees newly created reviews and publication of currently public collections from followed users, newest first, with pagination and links. Edits do not republish; a private collection made public gets a new publication time; private/deleted content disappears. |
| BR-13 | MVP-05 | **Review likes.** A user can like/unlike a review once, with accurate counts and current-user state; duplicate like does not create a second row. |
| BR-14 | MVP-05 | **Comments.** Signed-in users can post validated comments and delete their own comments. Unauthorized deletion fails; comments render safely and paginate if needed. |
| BR-15 | MVP-04 | **Bookmarks.** A signed-in user can save/remove a place and browse saved places independently of collections. Duplicate saves are prevented. |
| BR-16 | All | **Privacy and authorization.** Backend enforces ownership for profiles/reviews/comments/collections and visibility for private collections across API, MVC, search, and feed; UI hiding alone is insufficient. Guest access is limited to public resources. |
| BR-17 | MVP-05 | **User discovery.** Search by username/display name returns bounded results, opens public profiles, and reveals public reviews and collections without private information. |
| BR-18 | All | **Pagination.** Reviews, feeds, users, collections, and any large lists use repository-level bounded pagination, deterministic ordering, and valid page/size limits. |
| BR-19 | All | **Resilience.** Invalid requests, missing records, duplicate operations, unavailable database, and OSM service errors have appropriate API status/problem responses and helpful page states. An OSM outage leaves committed local content intact. |
| BR-20 | All | **Persistence and consistency.** All platform-generated users, reviews, interactions, collections, follows, bookmarks, and OSM-linked place references live in MySQL. Uniqueness, foreign keys, transactions, and migrations preserve data across application restarts. |

## Cross-cutting conditions

- Personal data, authentication, ownership, and collection visibility are enforced on the server. Public views omit private email, password hashes, and private collections.
- Place discovery uses actual OpenStreetMap-derived elements with attribution. A third-party service outage must not destroy previously stored reviews, collections, or saved places.
- Potentially large lists are bounded and paginated. Invalid input, duplicate actions, and missing resources produce understandable outcomes.
- All platform-generated data is persisted in MySQL with relationships and constraints that preserve consistency across restarts.
- Initial scope uses neutral imagery placeholders and has no media-upload flow. Uploaded avatars, review photos, and collection covers are optional future enhancements, not prerequisites for these requirements.

## University requirement coverage

This specification defines 20 business requirements, exceeding the university minimum of 10. They are grouped into five main MVP features in the companion document. Implementation evidence, testing results, and usage documentation will be published as the application is built and verified.

## Google discovery revision — 2026-11-19

The user replaced OSM/Overpass with Google Places API (New) and Maps JavaScript API. BR-03–05 discovery now uses Google identities, Google categories and 20-result candidate windows. Existing local review, account, collection and social requirements remain. Legacy OSM data is preserved; Google metadata is fetched on interaction and only Google IDs are retained for new places. This migration is authored and awaits verification.
