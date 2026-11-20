package com.locationreview.location_review_platform;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataIntegrationTests {
    private static final String SEED_PATH = "/tmp/location-review-sample-data.sql";

    @Test
    void seedIsExplicitAtomicAndRepeatableWithoutReplacingLocalContent() throws Exception {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(dataSource).load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();

            jdbc.update("""
                    insert into places (osm_type,osm_id,name,category,osm_tag_value,latitude,longitude,source,
                                        metadata_fetched_at,created_at,updated_at)
                    values ('WAY',16291869,'Existing park label','PARK','park',44.437,26.0907,'OSM',
                            UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            jdbc.update("""
                    insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                    values ('sample_daria','existing@example.invalid','test','Existing user','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            mysql.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/sample-data.sql")), SEED_PATH);

            ExecResult conflict = seed(mysql);
            assertThat(conflict.getExitCode()).isNotZero();
            assertThat(conflict.getStderr()).contains("Duplicate entry");
            assertThat(jdbc.queryForList("select username from users", String.class)).containsExactly("sample_daria");
            jdbc.update("update users set username = 'existing_user' where username = 'sample_daria'");

            jdbc.execute("alter table reviews add constraint ck_fixture_rating check (rating = 1)");
            ExecResult rejectedReview = seed(mysql);
            assertThat(rejectedReview.getExitCode()).isNotZero();
            assertThat(rejectedReview.getStderr()).contains("ck_fixture_rating");
            assertThat(jdbc.queryForList("select username from users", String.class)).containsExactly("existing_user");
            assertThat(jdbc.queryForList("select name from places", String.class)).containsExactly("Existing park label");
            jdbc.execute("alter table reviews drop check ck_fixture_rating");

            ExecResult firstRun = seed(mysql);
            assertThat(firstRun.getExitCode()).as(firstRun.getStderr()).isZero();
            assertThat(firstRun.getStdout()).contains("Sample data added.");
            jdbc.update("update reviews set body = 'Edited sample review' order by id limit 1");
            ExecResult rerun = seed(mysql);
            assertThat(rerun.getExitCode()).as(rerun.getStderr()).isZero();
            assertThat(rerun.getStdout()).contains("Sample data is already present.");

            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("select count(*) from places", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select name from places where osm_id = 16291869", String.class))
                    .isEqualTo("Existing park label");
            assertThat(jdbc.queryForObject("select count(*) from reviews", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("select body from reviews order by id limit 1", String.class))
                    .isEqualTo("Edited sample review");
            assertThat(jdbc.queryForObject("select count(*) from collections where published_at is not null", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from collections where visibility = 'PRIVATE'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from collection_places", Integer.class)).isEqualTo(8);
            assertThat(jdbc.queryForObject("select count(*) from follows", Integer.class)).isEqualTo(6);
            assertThat(jdbc.queryForObject("select count(*) from review_likes", Integer.class)).isEqualTo(6);
            assertThat(jdbc.queryForObject("select count(*) from review_comments", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from bookmarks", Integer.class)).isEqualTo(6);
        }
    }

    private ExecResult seed(MySQLContainer mysql) throws Exception {
        return mysql.execInContainer("sh", "-c", """
                MYSQL_PWD=$MYSQL_PASSWORD exec mysql --batch --skip-reconnect --default-character-set=utf8mb4 \
                    --host=127.0.0.1 --user=$MYSQL_USER $MYSQL_DATABASE < %s
                """.formatted(SEED_PATH));
    }
}
