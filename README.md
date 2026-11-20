# Location Review Platform

Location Review Platform is a Spring Boot web application for finding real places and sharing local reviews, collections, and recommendations with other people.

- **Accounts and profiles** support registration, sign-in, public profiles, and profile settings.
- **Explore** searches Google Places by name or area and shows nearby results in a list and on a map.
- **Reviews** support one rating and review per user and place, with community rating aggregates.
- **Collections and Saved** organize places in public or private guides and in a separate personal bookmark list.
- **Social activity** includes People search, follows, a feed, and review likes and comments.

The application uses Java 21, Spring Boot, Spring MVC and Security, Thymeleaf, vanilla JavaScript, Spring Data JPA, MySQL 8.4, Flyway, and springdoc OpenAPI. MVC pages and REST controllers share the same services and database. Google supplies place discovery and map data. Community content is stored in MySQL. Existing OpenStreetMap records remain readable with their original attribution. See [Architecture](docs/architecture.md) and [Place discovery](docs/place-discovery.md).

## Repository layout

```text
docs/                              Requirements, technical guides, and usage
location-review-platform/
  compose.yaml                     MySQL service and named volume
  run.ps1                          Windows startup
  src/main/java/                   Controllers, services, and persistence
  src/main/resources/              Thymeleaf, CSS, JavaScript, and Flyway migrations
  src/test/java/                   Unit, MockMvc, and MySQL integration tests
```

## Start on Windows

Install a Java 21 JDK and Docker Desktop with Docker Compose v2. Start Docker Desktop. The Maven wrapper supplies Maven and downloads dependencies on the first build.

In PowerShell, enter the application directory and create the ignored configuration file if it is missing.

```powershell
Set-Location .\location-review-platform
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Set `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env`. Use the original credentials if the MySQL volume already exists. For live discovery and maps, set `GOOGLE_MAPS_SERVER_API_KEY` and `GOOGLE_MAPS_BROWSER_API_KEY` as described in [Place discovery](docs/place-discovery.md).

```powershell
.\run.ps1
```

The script loads unset credentials from `.env`, checks Java 21, starts MySQL and waits for its health check, then runs Spring Boot. Compose initializes `location_review_db` and the `location_review` application user on a fresh volume. Flyway runs the migrations when Spring Boot connects.

| Local destination | Address |
| --- | --- |
| Website | [localhost:7171](http://localhost:7171/) |
| Swagger UI | [localhost:7171/swagger-ui.html](http://localhost:7171/swagger-ui.html) |
| OpenAPI JSON | [localhost:7171/v3/api-docs](http://localhost:7171/v3/api-docs) |
| MySQL | `127.0.0.1:3307` |

For manual Maven startup and IntelliJ configuration, follow [Usage](docs/usage.md). Stop Spring Boot with Ctrl+C. From the application directory, `docker compose stop` stops MySQL and preserves the named volume.

## Tests and usage

```powershell
.\mvnw.cmd -q clean verify
```

Run from `location-review-platform/` with Docker available. The tests use disposable MySQL and local provider mocks. See [Testing](docs/testing.md) for targeted commands and verification limits.

To populate your existing local database on demand, see [Local sample data](docs/testing.md#local-sample-data).

See [Usage](docs/usage.md) for site workflows and the [API guide](docs/api.md) for JSON operations.

## Troubleshooting and security

- **Wrong Java version**. Check both `java --version` and `JAVA_HOME`. The Maven wrapper uses `JAVA_HOME` when it is set.
- **MySQL is unhealthy or login fails**. Check that Docker Desktop is running and port 3307 is free. Use the original password for an existing volume. See [Usage](docs/usage.md) for read-only status commands.
- **Search or map is unavailable**. Check the corresponding Google key and its restrictions. The search list works independently of the browser map. Stored community activity remains available when place metadata cannot be refreshed.
- **API mutation returns 401 or 403**. Keep the signed-in session cookie and include the CSRF token from the same session. Check ownership for user-generated content.

Keep `.env` private and restrict the two Google keys to their intended APIs and client types. Passwords are stored as BCrypt hashes. Public pages and APIs omit account emails and enforce collection visibility on the server. The local configuration uses HTTP. Configure HTTPS before deploying outside a development environment.

Possible extensions include account recovery, media uploads, and accessibility testing with assistive technology.
