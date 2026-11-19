# Collections

Collections are ordered guides of up to 100 distinct, already resolved places. Signed-in users can create an empty collection, edit its title and description, switch visibility, add or remove places, reorder them, and delete the collection. Title is required (1–100 trimmed characters); description is optional (up to 1000 trimmed characters).

## Website

Browse public guides at `/collections`. Sign in and select **My collections** to include your private guides, or **Create a collection** to start one. Collection detail shows the ordered places, with links to their current details and maps. Google place names are not retained in the database, so a link may say **View place** until its detail page retrieves current provider information.

To collect a place, find it in Explore and open its detail page. If needed, select **Enable reviews and collections** to resolve its verified identity, then **Add to a collection** and choose one of your collections. The collection detail also accepts the numeric ID from an already resolved `/places/ID` address. Use **Move up** and **Move down** to reorder by keyboard or pointer. Removing a place compacts the remaining order. Deleting a collection asks for confirmation and keeps the underlying places and reviews.

Public profiles show only public collections, even when viewed by their owner. Manage private collections through **My collections**. Review and collection pagination are independent on profiles.

## Privacy and publication

Private collections are available only to their owner; other viewers receive 404 for direct reads and mutations. Public collections can be read by anyone; only the owner may change them (other signed-in users receive 403). All mutations require authentication and CSRF protection. Private guides are excluded from public discovery, previews and profile lists.

Creating a public collection sets its publication time. Public-to-private clears that time; private-to-public assigns a new one. Editing an already public guide or changing its items preserves its publication time. Collection edits update its modification time. Social feed integration is not yet available.

## JSON API

All paths below start with `/api/v1`. Request and response schemas are available at `/v3/api-docs` and in Swagger UI.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/collections?scope=public` | Public guides, newest publication first. |
| GET | `/collections?scope=mine` | Signed-in owner's guides, newest modification first. |
| GET | `/users/{id}/collections` | Public guides for an existing user. |
| POST | `/collections` | Create from `{title, description?, visibility}`; 201 with Location. |
| GET | `/collections/{id}` | Read metadata and ordered `places`. |
| PATCH | `/collections/{id}` | Change at least one of title, description, visibility. |
| DELETE | `/collections/{id}` | Delete collection and memberships; 204. |
| POST | `/collections/{id}/places` | Append `{placeId}`; 200 with current collection. |
| PUT | `/collections/{id}/places/order` | Replace order using `{placeIds:[...]}`; 200. |
| DELETE | `/collections/{id}/places/{placeId}` | Remove membership and compact order; 204. |

Lists return `{items, page, size, totalElements, totalPages}`. Page is zero-based; size defaults to 20 and must be 1–50. Sorts use descending ID as a deterministic tiebreaker. Summaries include up to three ordered `previewPlaces`; detail returns the complete ordered `places`. No account email or password hash is exposed.

Order replacement must contain each current place exactly once, including an empty list for an empty collection. Stale/nonmatching orders, duplicate memberships and additions past 100 return 409. Invalid input returns 400; missing collections, places or memberships return 404. Database row locks serialize collection mutations; two-stage position updates avoid intermediate unique-position collisions. The database also enforces unique collection/place and collection/position pairs. Collection deletion cascades only to memberships.

## Automated verification

`CollectionServiceTests` covers validation, privacy, authorization, missing targets, publication behavior and stale orders. `CollectionIntegrationTests` uses disposable MySQL 8.4 and a mocked Google client for API and HTML workflows, pagination, visibility, CSRF, ordering, concurrent additions, the 100-place boundary and deletion. Run the full Java 21 suite with `mvnw.cmd clean verify` from `location-review-platform/`.

Manual browser layout, assistive technology and live Google behavior have not been verified. Automated verification does not use the developer database or named volume. [Saved places](bookmarks.md) are managed independently of collections.
