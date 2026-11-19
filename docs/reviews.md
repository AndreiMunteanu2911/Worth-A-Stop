# Reviews and community ratings

A signed-in user may write one review per resolved place. The rating is an integer from 1 to 5; review text is trimmed and must contain 10–5000 characters. An optional visit date cannot be later than the current date in Europe/Bucharest. The author can edit or delete the review. Deleting a review also removes its likes and comments through database foreign-key cascades.

Place and public profile pages show reviews with pagination. Place reviews can be sorted by newest, oldest, highest or lowest rating. Ratings are calculated from the application's reviews; an unrated place has no average. For a new Google place, select **Enable reviews and collections** before writing a review. **Save place** also resolves the place and adds it to your saved list. Google metadata is fetched on interaction, and local reviews remain stored if the provider is unavailable.

The session-based JSON API exposes `POST /api/v1/reviews`, `GET/PATCH/DELETE /api/v1/reviews/{id}`, `GET /api/v1/places/{id}/reviews`, `GET /api/v1/places/{id}/rating-summary`, and `GET /api/v1/users/{id}/reviews`. Mutation requires authentication and CSRF. PATCH accepts any of `rating`, `body` and `visitDate`; explicitly null `visitDate` clears it. Lists use zero-based `page` and `size` (1–50) and return `items`, `page`, `size`, `totalElements` and `totalPages`. Place review sort accepts `newest`, `oldest`, `rating_high` or `rating_low`.

Review likes and comments are planned separately and have no user interface or API yet. Automated verification uses isolated MySQL and mock providers; live browser and provider workflows have not been verified.
