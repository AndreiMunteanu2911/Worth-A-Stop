# Reviews and community ratings

A signed-in user may write one review per resolved place. The rating is an integer from 1 to 5; review text is trimmed and must contain 10–5000 characters. An optional visit date cannot be later than the current date in Europe/Bucharest. The author can edit or delete the review. Deleting a review also removes its likes and comments through database foreign-key cascades.

Place and public profile pages show reviews with pagination. Place reviews can be sorted by newest, oldest, highest or lowest rating. Ratings are calculated from the application's reviews; an unrated place has no average. For a new Google place, select **Enable reviews and collections** before writing a review. **Save place** also resolves the place and adds it to your saved list. Google metadata is fetched on interaction, and local reviews remain stored if the provider is unavailable.

The session-based JSON API exposes `POST /api/v1/reviews`, `GET/PATCH/DELETE /api/v1/reviews/{id}`, `GET /api/v1/places/{id}/reviews`, `GET /api/v1/places/{id}/rating-summary`, and `GET /api/v1/users/{id}/reviews`. Mutation requires authentication and CSRF. PATCH accepts any of `rating`, `body` and `visitDate`; explicitly null `visitDate` clears it. Lists use zero-based `page` and `size` (1–50) and return `items`, `page`, `size`, `totalElements` and `totalPages`. Place review sort accepts `newest`, `oldest`, `rating_high` or `rating_low`.

## Likes and comments

Open **Discussion** from a review on a place or profile page. Anyone can read the review and its comments. Sign in to like or unlike the review, post a comment, or delete a comment you wrote. Comments appear oldest first and are paginated. Comment text is trimmed, must contain 1–1000 characters, and is displayed as plain text.

| Method | Path beneath `/api/v1` | Result |
| --- | --- | --- |
| POST | `/reviews/{id}/likes` | 200 with `liked: true` and `likeCount`. A repeated like preserves the original timestamp. |
| DELETE | `/reviews/{id}/likes` | 204. An already absent like on an existing review also returns 204. |
| GET | `/reviews/{id}/comments` | Public page of comments ordered by creation time and ID, both ascending. |
| POST | `/reviews/{id}/comments` | 201 with the created comment and `Location: /api/v1/comments/{id}`. Send a JSON string in `body`. |
| DELETE | `/comments/{id}` | 204 for the comment author, 403 for another signed-in user, or 404 if absent. |

All mutations require a signed-in session and a CSRF token. Invalid input returns 400, and a missing review returns 404. Review responses include the total `likeCount` and the viewer-specific `likedByMe`, which is false for guests. Comment responses include the ID, review ID, public author summary, text, and creation timestamp. See `/v3/api-docs` or Swagger UI for the schemas.

Automated verification uses isolated MySQL and mock providers. Manual browser and live provider workflows remain unverified.
