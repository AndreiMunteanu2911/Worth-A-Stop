# Five main MVP features — Location Review Platform

**Document status:** approved intended MVP scope. This is a requirements document; it does not claim that the application is already implemented. These five areas derive from [the business requirements](business-requirements.md) and together describe the complete first product release. Each area includes a graphical interface and corresponding REST functionality.

## MVP-01 — Accounts and public profiles

A visitor can create an account, sign in, and sign out. A registered user can edit their own username, email, display name, and biography. Public profiles show identity, biography, published reviews, public collections, and follower/following counts without exposing email, password data, or private collections. An initial-based avatar placeholder is used; photo uploads are outside the initial scope.

**Business requirements:** BR-01, BR-02, BR-16, BR-18, BR-19, BR-20.

**Acceptance outcomes:** duplicate usernames/emails are rejected; passwords are securely hashed; guests cannot change protected content; owner edits succeed and another user cannot edit the profile; public information is safe and paginated where needed. Registration, login, and profile management are usable through the website, with relevant profile and registration REST operations.

## MVP-02 — Real place discovery and search

Visitors search actual OpenStreetMap places by name and geographic area, explore nearby places on an interactive Leaflet map, browse a coordinated result list, filter supported categories and locally available rating information, and open a place detail page. The detail page combines available location metadata with the platform's own review statistics and relevant real-place recommendations. Search results and map data display source attribution and explain missing or unavailable data.

**Business requirements:** BR-03, BR-04, BR-05, BR-18, BR-19, BR-20.

**Acceptance outcomes:** map pan/zoom, markers, popups, list fallback, search, filters, sorting, and detail navigation work on desktop and mobile; every reviewable place is linked to a verified OSM element; repeated discovery does not create duplicate places; provider failures leave existing local content available. Search requests are bounded and comply with provider policies.

## MVP-03 — Ratings and reviews

An authenticated user can publish one current review for a place with an integer rating from 1 to 5, written content, and an optional visit date that cannot be in the future. The author may edit or delete that review. Visitors can read paginated reviews on place and profile pages. A place's average, count, and rating distribution come only from stored platform reviews and update after review changes.

**Business requirements:** BR-06, BR-07, BR-08, BR-16, BR-18, BR-19, BR-20.

**Acceptance outcomes:** invalid ratings, dates, content, and duplicate author/place reviews are rejected; only the author can edit or delete; creation, editing, and deletion produce accurate aggregate results, including an empty place's unrated state. Review forms and reading pages work alongside REST operations.

## MVP-04 — Curated collections and saved places

Registered users can create public or private collections of real places, edit their title and description, add or remove places, change their order, switch visibility, and delete collections. Empty collections are valid. A separate saved-places area supports quick bookmarks without turning them into curated collections. Public collections are discoverable; private collections are visible only to their owner.

Website workflows and JSON operations are documented in [Collections](collections.md) and [Saved places](bookmarks.md).

**Business requirements:** BR-09, BR-10, BR-15, BR-16, BR-18, BR-19, BR-20.

**Acceptance outcomes:** duplicate collection items and bookmarks do not create duplicate records; ordering survives refresh/restart; visibility changes immediately affect public discovery and feed eligibility; unauthorized reads or edits fail; collection and bookmark workflows work through website controls and REST operations.

## MVP-05 — Social discovery and interactions

People can find users by username or display name and view public profiles. A registered user can follow or unfollow others, read a paginated feed of followed users' new reviews and currently public collection publications, like or unlike reviews, and write or delete their own review comments. Counts, authorship, and timestamps reflect stored data. Private or deleted content is absent from the feed.

Website workflows and JSON operations for profile search and following are documented in [People and follows](people.md).

**Business requirements:** BR-11, BR-12, BR-13, BR-14, BR-17, BR-18, BR-19, BR-20.

**Acceptance outcomes:** self-follow and duplicate relationships are prevented; follow/like/bookmark state and counts remain accurate; comments are validated and only their authors may delete them; feed order and pagination are stable; private collections never leak through public profiles or feeds. User search, feed, follow, like, and comment flows work in the interface and REST API.

## Shared MVP quality criteria

The first release is complete only when all five areas function through the GUI and documented REST API, persisted data survives restart, validation and access controls are enforced server-side, and the full build and tests pass. The interface uses a cohesive editorial design, responsive layouts, accessible controls, meaningful loading/empty/error states, and real data without fabricated places or reviews. Swagger/OpenAPI documentation must describe every custom REST endpoint. Detailed usage, API, testing, and demonstration documents will describe verified behavior when implementation is complete.

Google provider revision: MVP-02 is now implemented in code against Google Places API (New) and Google Maps JavaScript. New place identities use Google IDs; existing OSM records remain readable. Nearby candidate limit is 20. The migration's execution and verification are pending; earlier OSM checks do not establish Google behavior.
