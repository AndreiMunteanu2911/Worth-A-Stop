# Activity feed

Sign in and open **Feed** to see reviews and public collections from accounts you follow. Open an author's name to view their profile, or select the item title to read the review discussion or collection. If your feed is empty, use **Find people to follow** to get started.

The feed uses the accounts you currently follow, including their earlier public activity. Reviews appear by creation time and public collections by publication time. Editing either item can change the displayed text while preserving its position. Making a collection private removes it from the feed. Making it public again gives it a new publication time. Deleted items and activity from accounts you unfollow disappear.

## JSON API

`GET /api/v1/feed` uses the signed-in session and returns `items`, `page`, `size`, `totalElements`, and `totalPages`. The first page is 0. The default size is 20, and the allowed range is 1–50. Empty pages return 200. Invalid pagination returns 400, and guests receive 401.

- `kind` is `REVIEW_PUBLISHED` or `COLLECTION_PUBLISHED`.
- `eventAt` is the UTC publication timestamp. Results sort by timestamp descending, kind ascending, then target ID descending.
- `actor` contains the public account ID, username, and display name.
- `target` contains the item ID, a site-relative URL, a title, and a review excerpt. Collection excerpts are null. Review excerpts contain up to 240 characters of text, followed by an ellipsis when shortened.

The page reads locally stored reviews and collections, so loading it requires no live place metadata. Review titles use the stored place name when available and show **Place review** otherwise. Swagger UI and `/v3/api-docs` describe the response and session requirements.
