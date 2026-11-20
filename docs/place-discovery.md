# Google place discovery

Discovery uses Places API (New) for explicit text search, nearby discovery and verified place details, and Maps JavaScript API for map display.

## Configuration

Keep the two credentials in the Git-ignored application `.env` or process environment:

```
GOOGLE_MAPS_SERVER_API_KEY=
GOOGLE_MAPS_BROWSER_API_KEY=
```

`run.ps1` loads these names along with the existing MySQL credentials. Spring Boot launched directly from an IDE needs the same environment variables. The browser key is sent to discovery pages for Maps JavaScript API; restrict it to the application's website origins. The server key stays on the backend and is sent to Places API in an HTTP header. Restrict it to Places API (New) and the server's public IP. The application does not read keys from source files. Existing `.env` credentials are preserved.

## Routes and behavior

Home and Explore both support text search. Explore renders the result list and its attribution when JavaScript is disabled. With JavaScript enabled, Explore updates the list without a full page load and keeps the previous results if the provider fails. On a narrow screen, use **List** and **Map** to switch views. When the map is available, focusing a place link or selecting its pin highlights the same result.

- `GET /api/v1/places/search?q=...`: 2–100 character query, optional complete south/west/north/east rectangle (at most 0.25 degrees per axis), optional lat/lon center pair, category, minimum community rating, sort and limit.
- `GET /api/v1/places/nearby?lat=...&lon=...`: radius 100–5000 meters; default 1500; maximum 20 candidates.
- `GET /api/v1/places/google/{googlePlaceId}`: transient details without persistence.
- `GET /api/v1/places/{id}`: local identity plus fresh Google details, or preserved legacy OSM snapshot.
- `POST /api/v1/places/resolve`: authenticated and CSRF-protected; body `{ "googlePlaceId": "..." }`; verifies through Google and retains the unique Google ID and application timestamps.
- `GET /api/v1/places/{id}/recommendations`: up to five reviewed local candidates, filtered using fresh category and proximity (within 10 km).

Search and nearby are bounded candidate windows, not complete inventories. `truncated` indicates a full provider window or a shorter application limit. Categories are restaurant, cafe, museum, park, lodging and shopping_mall. Minimum rating and rating sort use this application's community reviews, never Google's rating. Candidates without community reviews do not satisfy a minimum rating. Search supports relevance, distance (requires center) and rating; nearby supports distance and rating. Both accept limits 1–20.

## Persistence and unavailable providers

V2 adds a case-sensitive unique Google place ID and allows metadata columns to be null. New Google rows retain IDs only, with a database constraint preventing permanent Google name/address/coordinate snapshots. No automatic caching, provider retries or background crawling is implemented. Read requests do not write database rows.

V1 and existing OSM rows and all associated user content remain intact. No automatic OSM-to-Google match is attempted. New requests use Google only. A saved Google identity remains readable with its community rating when metadata cannot be fetched; the response declares `metadataUnavailable=true` and the UI explains that details are unavailable. Remote-only lookup errors remain errors. Legacy details show OSM provenance.

Google attribution and third-party attribution supplied with results are displayed. Searches request a limited field mask, excluding Google reviews, ratings and photos. Detail requests also request website, telephone and opening hours when available; those fields can select a higher Google billing tier. These fields are displayed but never retained in the database. The application's privacy and terms pages link to Google's terms/privacy information. Google metadata is fetched on interaction and is not used as a permanent business directory. Before publication, review the applicable EEA terms for the billing account and deployed use case.

## Verification

Automated verification uses disposable MySQL 8.4, local provider fixtures, and mocked network responses. Offline headless browser checks exercise responsive layout, keyboard navigation, result and marker selection, and the list fallback when the map fails. Live Google behavior and hands-on assistive-technology checks remain unverified.
