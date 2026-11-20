# Worth a Stop

Worth a Stop is a Spring Boot web application for finding real places and sharing local reviews, collections, and recommendations with other people.

- **Accounts and profiles** support registration, sign-in, public profiles, and profile settings.
- **Explore** searches Google Places by name or area and shows nearby results in a list and on a map.
- **Reviews** support one rating and review per user and place, with community rating aggregates.
- **Collections and Saved** organize places in public or private guides and in a separate personal bookmark list.
- **Social activity** includes People search, follows, a feed, and review likes and comments.

The application uses Java 21, Spring Boot, Spring MVC and Security, Thymeleaf, vanilla JavaScript, Spring Data JPA, MySQL 8.4, Flyway, and springdoc OpenAPI. MVC pages and REST controllers share the same services and database. Google supplies place discovery and map data. Community content is stored in MySQL. Existing OpenStreetMap records remain readable with their original attribution. See [Architecture](docs/architecture.md) and [Place discovery](docs/place-discovery.md).

## Repository layout

```text
docs/                              Requirements, technical guides, and usage
pom.xml                            Maven project configuration
compose.yaml                       MySQL service and named volume
run.ps1                            Windows startup
seed-sample-data.ps1               Optional local sample data import
scripts/                           Local sample data SQL
src/main/java/                     Controllers, services, and persistence
src/main/resources/                Thymeleaf, CSS, JavaScript, and Flyway migrations
src/test/java/                     Unit, MockMvc, and MySQL integration tests
```

## Start on Windows

Install a Java 21 JDK and Docker Desktop with Docker Compose v2. Start Docker Desktop. The Maven wrapper supplies Maven and downloads dependencies on the first build.

From the repository root in PowerShell, create the ignored configuration file if it is missing.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Set `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env`. Use the original credentials if the MySQL volume already exists. For live discovery and maps, set `GOOGLE_MAPS_SERVER_API_KEY` and `GOOGLE_MAPS_BROWSER_API_KEY` as described in [Place discovery](docs/place-discovery.md).

```powershell
.\run.ps1
```

The script loads unset credentials from `.env`, finds a Java 21 JDK, starts MySQL and waits for its health check, then runs Spring Boot. Compose initializes `location_review_db` and the `location_review` application user on a fresh volume. Flyway runs the migrations when Spring Boot connects.

| Local destination | Address |
| --- | --- |
| Website | [localhost:7171](http://localhost:7171/) |
| Swagger UI | [localhost:7171/swagger-ui.html](http://localhost:7171/swagger-ui.html) |
| OpenAPI JSON | [localhost:7171/v3/api-docs](http://localhost:7171/v3/api-docs) |
| MySQL | `127.0.0.1:3307` |

For manual Maven startup and IntelliJ configuration, follow [Usage](docs/usage.md). Stop Spring Boot with Ctrl+C. From the repository root, `docker compose stop` stops MySQL and preserves the named volume.

## Tests and usage

```powershell
.\mvnw.cmd -q clean verify
```

Run from the repository root with Docker available. The tests use disposable MySQL and local provider mocks. See [Testing](docs/testing.md) for targeted commands and verification limits.

To populate your existing local database on demand, see [Local sample data](docs/testing.md#local-sample-data).

See [Usage](docs/usage.md) for site workflows and the [API guide](docs/api.md) for JSON operations.

## Troubleshooting and security

- **Java 21 JDK not found**. The startup script checks `JAVA_HOME`, `PATH`, and common Windows installation folders. For a JDK installed elsewhere, point `JAVA_HOME` to its installation directory. When running the Maven wrapper directly, set `JAVA_HOME` to Java 21 first.
- **MySQL is unhealthy or login fails**. Check that Docker Desktop is running and port 3307 is free. Use the original password for an existing volume. See [Usage](docs/usage.md) for read-only status commands.
- **Search or map is unavailable**. Check the corresponding Google key and its restrictions. The search list works independently of the browser map. Stored community activity remains available when place metadata cannot be refreshed.
- **API mutation returns 401 or 403**. Keep the signed-in session cookie and include the CSRF token from the same session. Check ownership for user-generated content.

Keep `.env` private and restrict the two Google keys to their intended APIs and client types. Passwords are stored as BCrypt hashes. Public pages and APIs omit account emails and enforce collection visibility on the server. The local configuration uses HTTP. Configure HTTPS before deploying outside a development environment.

Possible extensions include account recovery, media uploads, and accessibility testing with assistive technology.
