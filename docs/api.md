# REST API

Open [Swagger UI](http://localhost:7171/swagger-ui.html) for request and response schemas or [OpenAPI JSON](http://localhost:7171/v3/api-docs) for the generated definition. The paths below are relative to `/api/v1`. Login and logout are Spring Security form endpoints at `POST /login` and `POST /logout`.

## Session, CSRF, and errors

The application uses a server-side session identified by the `JSESSIONID` cookie. Keep it in the same cookie jar across requests. A browser can log in through the website before opening Swagger UI. In an HTTP client, send form-encoded username or email in `username` and the password in `password` to `/login`, along with the CSRF form value. The home page exposes the active token and header name as `csrf-token` and `csrf-header` meta tags. For each JSON mutation, send that header with the token from the same session. Refresh the page and read a new token after authentication changes. Registration is public, requires CSRF, and signs in the new account.

Errors use `application/problem+json`. Common fields include `status`, `title`, `detail`, `code`, and optional `fieldErrors`. Expect 400 for validation, 401 for protected operations with no session, 403 for a rejected CSRF token or an unauthorized mutation, 404 for missing content or a private collection hidden from the viewer, and 409 for a conflict. Provider limits and outages can return 429 and 503.

Standard pages contain `items`, `page`, `size`, `totalElements`, and `totalPages`. Page numbering starts at 0, the default size is 20, and valid sizes are 1–50. Discovery returns a bounded candidate window with `items`, `limit`, and `truncated`. See [Place discovery](place-discovery.md).

## Accounts and people

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/auth/register` | Create an account and start a session. |
| GET | `/users/me` | Read the signed-in account. |
| PATCH | `/users/me` | Update profile fields. |
| GET | `/users/{id}` | Read a public profile. |
| GET | `/users` | Search by `q` in username or display name. |
| GET | `/users/{id}/followers` | Page through followers. |
| GET | `/users/{id}/following` | Page through followed accounts. |
| POST | `/users/{id}/follow` | Follow and return the current state. |
| DELETE | `/users/{id}/follow` | Remove the follow if present. |

## Place discovery and ratings

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/places/search` | Search by `q` with optional bounds and filters. |
| GET | `/places/nearby` | Search around `lat` and `lon` with optional radius and filters. |
| GET | `/places/google/{ref}` | Read details for a Google ID. |
| POST | `/places/resolve` | Verify `googlePlaceId` and return the durable local identity. |
| GET | `/places/{id}` | Read by local place ID. |
| GET | `/places/{id}/recommendations` | Read nearby reviewed places in the same category. |
| GET | `/places/{id}/rating-summary` | Read community review count, average, and distribution. |
| GET | `/places/{id}/reviews` | Page through reviews with optional sort. |

## Reviews, likes, and comments

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/reviews` | Publish one review for a resolved place. |
| GET | `/reviews/{id}` | Read a review and its like state. |
| PATCH | `/reviews/{id}` | Edit your review. A null `visitDate` clears the date. |
| DELETE | `/reviews/{id}` | Delete your review and its likes and comments. |
| GET | `/users/{id}/reviews` | Page through a user's reviews. |
| POST | `/reviews/{id}/likes` | Like and return the current count. |
| DELETE | `/reviews/{id}/likes` | Remove the like if present. |
| GET | `/reviews/{id}/comments` | Page through public comments, oldest first. |
| POST | `/reviews/{id}/comments` | Post a plain-text comment. |
| DELETE | `/comments/{id}` | Delete your comment. |

## Collections and bookmarks

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/collections` | Browse public collections. Use `scope=mine` to include your private collections. |
| GET | `/users/{id}/collections` | Browse a user's public collections. |
| POST | `/collections` | Create a public or private collection. |
| GET | `/collections/{id}` | Read a collection and its ordered places. |
| PATCH | `/collections/{id}` | Edit title, description or visibility. |
| DELETE | `/collections/{id}` | Delete your collection and its memberships. |
| POST | `/collections/{id}/places` | Append a resolved place. |
| PUT | `/collections/{id}/places/order` | Replace the complete order of current members. |
| DELETE | `/collections/{id}/places/{placeId}` | Remove a membership. |
| GET | `/bookmarks` | Page through your saved places. |
| POST | `/bookmarks` | Save a resolved place. |
| DELETE | `/bookmarks/{placeId}` | Remove the bookmark if present. |

## Feed

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/feed` | Read the signed-in user's paginated activity from followed accounts. |

The [user guides](usage.md#find-places-and-manage-content) explain the related website flows. Repeated saves, follows and likes on an existing target succeed without creating a duplicate relationship.
