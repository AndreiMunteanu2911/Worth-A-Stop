# Usage

## Start the application

The [README](../README.md#start-on-windows) describes the PowerShell runner. To start the services separately, run these commands from the repository root.

```powershell
docker compose up -d --wait
# Load the same MYSQL_PASSWORD and optional Google keys into this PowerShell process first.
.\mvnw.cmd spring-boot:run
```

Docker Compose reads `.env` for its own substitutions. Spring Boot launched directly by Maven reads process environment variables. Set `MYSQL_PASSWORD` to the same value used to initialize the MySQL user and provide `GOOGLE_MAPS_SERVER_API_KEY` and `GOOGLE_MAPS_BROWSER_API_KEY` for live discovery and map display. Set `MYSQL_ROOT_PASSWORD` for Compose. The Spring Boot application uses the regular MySQL user and requires no root credentials.

In IntelliJ IDEA, open the root `pom.xml` as a Maven project, select a Java 21 JDK for the project and run configuration, and start MySQL with Compose. Create a run configuration for `com.locationreview.location_review_platform.LocationReviewPlatformApplication` and give it the Spring environment variables described above. When IntelliJ invokes Maven, set its Maven runner JRE to Java 21 as well.

The website is at [localhost:7171](http://localhost:7171/). Use `docker compose ps` to check database health and `docker compose logs mysql` to inspect startup output. MySQL persists in `mysql_data`. Normal shutdown uses Ctrl+C for Spring Boot and optionally `docker compose stop` for MySQL. Flyway applies pending migrations to the existing schema. See [Database design](database-design.md).

## Find places and manage content

1. Register or log in, then open **Explore**. Search by name or area or select **Find places near me** and grant location permission. Filter the returned places by category or minimum community rating.
2. Open a place. Select **Save place** to add it to **Saved**. The app verifies new Google places when you save them, start a review, or choose to add them to a collection.
3. Select **Write a review** to rate the place. Use **Edit review** or the deletion confirmation to manage your existing review.
4. Create a collection under **Collections**. Open a place and choose **Add to a collection**. Manage visibility and order from the collection page. **Saved** is a separate personal list.
5. Open **People**, find another account, and follow it from its profile. View followed activity under **Feed**. Choose **Discussion** on a review to like it, comment, or delete your own comment.
