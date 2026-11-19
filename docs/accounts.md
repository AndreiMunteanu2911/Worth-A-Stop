# Accounts and profiles

Visitors can register at `/register` and sign in at `/login` with a username or email. Registration signs the user in. Sign-out uses the navigation's POST form. Authenticated users can edit their own profile at `/settings/profile`; `/users/{id}` displays a public profile. Public profiles show independently paginated reviews and public collections. Private collections are accessible through the owner's collection list. See [Collections](collections.md).

Usernames are trimmed and lowercased and must contain 3–30 ASCII letters, digits or underscores. Emails are trimmed, lowercased, validated and limited to 254 characters. Display names are required and limited to 80 characters; biographies are optional and limited to 500 characters. Passwords must be 12–72 UTF-8 bytes and are stored using BCrypt. Username and email are unique. Public profiles do not expose email or password hashes.

The JSON API provides `POST /api/v1/auth/register`, `GET /api/v1/users/me`, `PATCH /api/v1/users/me`, and `GET /api/v1/users/{id}`. API mutations require a CSRF token; the account endpoints use the same server-side session as the website. Registration returns 201 and starts a session. The self endpoints require authentication. Public profile responses contain no email or password hash. The OpenAPI document at `/v3/api-docs` and Swagger UI describe these operations.

Public profiles include linked follower and following counts. Visit [People and follows](people.md) for profile search, connection lists, and follow controls.

Place discovery is available at `/explore`, the default destination after login. Password changes and account deletion are not available.
