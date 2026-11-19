# Saved places

Sign in and select **Save place** on a place detail page. For a new Google result, the app verifies and resolves the Google place before adding the bookmark. **Saved · Remove** removes it from your list. To use a remote place for reviews and collections without bookmarking it, select **Enable reviews and collections**.

Open **Saved** in the navigation to see your private list, newest first. Each entry opens its place details and has a **Remove** button. Removing a bookmark leaves the place, reviews and collection memberships intact. Saving a place again while it is already saved keeps its original position in the list.

The list shows stored names, categories and addresses for legacy OpenStreetMap places when available. Google entries show a local place number and link to their current details. Browsing and removing saved places require no Google requests.

## JSON API

All three operations use the signed-in session. POST and DELETE also require a CSRF token.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/bookmarks` | 200 with `{items, page, size, totalElements, totalPages}`, sorted by creation time then bookmark ID, both descending. |
| POST | `/api/v1/bookmarks` | Accepts `{"placeId":42}` and returns 200 with `{"saved":true,"placeId":42}` for both first and repeated saves. |
| DELETE | `/api/v1/bookmarks/{placeId}` | 204 for an existing place, including when it was already absent from your list. |

Each list item has `createdAt` and `place` with `id`, `source`, and nullable `name`, `category`, and `address`. The zero-based `page` defaults to 0 and `size` defaults to 20, with a range of 1–50. A page offset must fit in a signed 32-bit integer. Invalid IDs or pagination return 400, missing sessions return 401, rejected CSRF checks return 403, and writes targeting absent places return 404. All three operations appear at `/v3/api-docs` and in Swagger UI.
