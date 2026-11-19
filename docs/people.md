# People and follows

Open **People** to find a profile by username or display name. Search matches partial text without regard to case and shows results in username order. The search term must contain 2–80 characters after trimming. Each result links to a public profile.

Sign in and select **Follow** on another user's profile. Select **Unfollow** to remove the connection. The profile displays current follower and following counts. Select either count to browse the corresponding public list, newest connections first. Profile and directory results show public account information only.

## JSON API

All paths start with `/api/v1`. OpenAPI schemas are available at `/v3/api-docs` and in Swagger UI.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/users?q=reader` | Search public profiles by username or display name. |
| GET | `/users/{id}/followers` | List public profiles of users who follow this account. |
| GET | `/users/{id}/following` | List public profiles of accounts this user follows. |
| POST | `/users/{id}/follow` | Return 200 with `{following: true, followerCount}`. Repeated calls preserve the relationship's original timestamp. |
| DELETE | `/users/{id}/follow` | Return 204 for an existing user, including when the relationship is already absent. |

List results contain `items`, `page`, `size`, `totalElements`, and `totalPages`. Page defaults to 0. Size defaults to 20 and accepts 1–50. Connection lists sort by relationship creation time and use descending relationship ID to break ties. Empty results return 200.

Mutations require a signed-in session and a CSRF token. Invalid input returns 400, missing users return 404, and either follow mutation targeting the caller returns 409. Following is directional. The unique database key prevents duplicate relationships, including concurrent requests.
