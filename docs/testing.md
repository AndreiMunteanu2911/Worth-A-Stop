# Testing

Run from `location-review-platform/` with a Java 21 JDK and Docker Desktop running. Set `JAVA_HOME` to Java 21 when multiple JDKs are installed.

```powershell
.\mvnw.cmd -q clean verify
.\mvnw.cmd -q '-Dtest=FoundationWebTests,ReviewServiceTests' test
.\mvnw.cmd -q '-Dtest=GooglePlaceIntegrationTests,FeedIntegrationTests' test
```

- Unit tests exercise validation, ownership, mapping, pagination, and relationship rules.
- MockMvc tests cover MVC rendering, form sessions, CSRF, the generated OpenAPI inventory and Swagger UI, and API requests.
- MySQL integration tests start disposable MySQL 8.4 containers, apply both Flyway migrations, and validate Hibernate mappings. They check constraints, cascades, concurrent writes, and persistence after restarting an isolated database.
- Provider tests use a local HTTP fixture and mocks. The Explore and map scripts have also been exercised in headless Edge by serving rendered fixtures and intercepting network requests.

Java 21 `clean verify` passed all 97 tests with zero failures or skips and built `target/location-review-platform-0.0.1-SNAPSHOT.jar`. Surefire results are under `target/surefire-reports/`. Delivery checks also validated the Compose configuration and exercised the PowerShell runner with mocked Docker and Maven commands.

Automated tests cover account, discovery, review, collection, bookmark, and social workflows. Live Google requests and hands-on assistive-technology testing remain unverified.

## Local sample data

Start the app once so Flyway initializes the database, then run this command in a second PowerShell window from `location-review-platform/`.

```powershell
.\seed-sample-data.ps1
```

The command uses the existing Compose MySQL service and adds four labeled sample profiles, reviews, collections, bookmarks, likes, comments, and follows. Existing records and edits are preserved. Running it again skips the import. Normal startup applies schema migrations and leaves sample data opt-in.

Refresh Home to see recent reviews and collections. To try the feed, register or sign in with your own account, then follow **Mara (sample)** or another sample profile through People. Sample profiles have no shared login credentials. The four sample places use [OpenStreetMap data](https://www.openstreetmap.org/copyright) and can be opened from Home or the sample collections. The reviews and other community activity are fictional.
