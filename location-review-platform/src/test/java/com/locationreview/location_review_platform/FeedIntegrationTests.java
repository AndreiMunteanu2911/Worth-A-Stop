package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.places.GooglePlacesClient;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FeedIntegrationTests {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final String API = "/api/v1/feed";
    private static final String PASSWORD = "integration-password";
    private static final String EVENT_TIME = "2026-01-02 12:30:00.123456";
    static { MYSQL.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean GooglePlacesClient provider;
    private long reader, author, outsider, googlePlace, legacyPlace;

    @BeforeEach
    void setup() {
        jdbc.update("delete from follows");
        jdbc.update("delete from reviews");
        jdbc.update("delete from collections");
        jdbc.update("delete from places");
        jdbc.update("delete from users");
        String hash = encoder.encode(PASSWORD);
        jdbc.update("""
                insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                values ('reader','reader@example.test',?,'Reader','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('author','author@example.test',?,'Author','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('outsider','outsider@example.test',?,'Outsider','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, hash, hash, hash);
        reader = userId("reader");
        author = userId("author");
        outsider = userId("outsider");
        jdbc.update("""
                insert into places (google_place_id,source,created_at,updated_at)
                values ('ChIJ_feed_test','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        jdbc.update("""
                insert into places (osm_type,osm_id,name,category,osm_tag_value,latitude,longitude,source,
                                    metadata_fetched_at,created_at,updated_at)
                values ('NODE',99,'Old Park','PARK','park',44,26,'OSM',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        googlePlace = jdbc.queryForObject("select id from places where source='GOOGLE'", Long.class);
        legacyPlace = jdbc.queryForObject("select id from places where source='OSM'", Long.class);
    }

    @Test
    void authenticatesBothRoutesAndValidatesPagination() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("authentication_required"));
        mvc.perform(get("/feed")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/")).andExpect(content().string(not(containsString("href=\"/feed\""))));
        mvc.perform(get(API).with(user(Long.toString(reader)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/feed").with(user(Long.toString(reader))))
                .andExpect(content().string(containsString("Find people to follow")));
        for (String size : List.of("0", "51", "bad")) {
            mvc.perform(get(API).param("size", size).with(user(Long.toString(reader)))).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(get(API).param("page", "-1").with(user(Long.toString(reader))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_pagination"));
        mvc.perform(get(API).param("page", "2147483647").with(user(Long.toString(reader))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/feed").param("page", "-1").with(user(Long.toString(reader))))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("role=\"alert\"")));
    }

    @Test
    void databasePaginatesAcrossKindsAndFiltersBeforeCounting() throws Exception {
        follow(reader, author);
        follow(outsider, reader);
        String reviewText = "😀".repeat(241);
        long lowReview = insertReview(author, googlePlace, reviewText, EVENT_TIME);
        long highReview = insertReview(author, legacyPlace, "A second review", EVENT_TIME);
        long lowCollection = insertCollection(author, "Older ID", EVENT_TIME);
        long highCollection = insertCollection(author, "Newer ID", EVENT_TIME);
        insertReview(reader, googlePlace, "Own activity", "2026-01-04 12:00:00");
        insertReview(outsider, googlePlace, "Unfollowed activity", "2026-01-04 12:00:00");
        insertCollection(outsider, "Unfollowed collection", "2026-01-04 12:00:00");
        insertCollection(author, "Private title", null);

        long newest = insertCollection(author, "Newest event", "2026-01-03 12:00:00");
        feed(0, 2).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].target.id").value(newest))
                .andExpect(jsonPath("$.items[1].target.id").value(highCollection))
                .andExpect(jsonPath("$.items[1].kind").value("COLLECTION_PUBLISHED"))
                .andExpect(jsonPath("$.items[1].eventAt").value("2026-01-02T12:30:00.123456Z"))
                .andExpect(jsonPath("$.items[1].actor.email").doesNotExist())
                .andExpect(jsonPath("$.items[1].actor.passwordHash").doesNotExist());
        feed(1, 2).andExpect(jsonPath("$.items[0].target.id").value(lowCollection))
                .andExpect(jsonPath("$.items[0].target.url").value("/collections/" + lowCollection))
                .andExpect(jsonPath("$.items[1].kind").value("REVIEW_PUBLISHED"))
                .andExpect(jsonPath("$.items[1].target.url").value("/reviews/" + highReview))
                .andExpect(jsonPath("$.items[1].target.title").value("Review of Old Park"));
        feed(2, 2).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].target.id").value(lowReview))
                .andExpect(jsonPath("$.items[0].target.title").value("Place review"))
                .andExpect(jsonPath("$.items[0].target.excerpt").value("😀".repeat(240) + "…"));
        feed(3, 2).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(5));
        verifyNoInteractions(provider);
    }

    @Test
    void twoSessionsSeePublicationChangesWithoutRepublishingEdits() throws Exception {
        MockHttpSession readerSession = signIn("reader");
        MockHttpSession authorSession = signIn("author");
        var reviewResult = mvc.perform(post("/api/v1/reviews").session(authorSession).with(csrf())
                        .contentType("application/json")
                        .content("{\"placeId\":" + googlePlace + ",\"rating\":4,\"body\":\"<b>Local review</b>\"}"))
                .andExpect(status().isCreated()).andReturn();
        long review = jdbc.queryForObject("select id from reviews", Long.class);
        String reviewApi = reviewResult.getResponse().getHeader("Location");
        var collectionResult = mvc.perform(post("/api/v1/collections").session(authorSession).with(csrf())
                        .contentType("application/json").content("{\"title\":\"Private itinerary\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String collectionApi = collectionResult.getResponse().getHeader("Location");
        long collection = jdbc.queryForObject("select id from collections", Long.class);
        jdbc.update("update reviews set created_at=? where id=?", EVENT_TIME, review);
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(post("/api/v1/users/{id}/follow", author).session(readerSession).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/feed").session(readerSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/feed\" aria-current=\"page\"")))
                .andExpect(content().string(containsString("href=\"/users/" + author + "\"")))
                .andExpect(content().string(containsString("href=\"/reviews/" + review + "\"")))
                .andExpect(content().string(containsString("datetime=\"2026-01-02T12:30:00.123456Z\"")))
                .andExpect(content().string(containsString("2 Jan 2026, 14:30")))
                .andExpect(content().string(containsString("&lt;b&gt;Local review&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("Private itinerary"))));
        change(authorSession, collectionApi, "{\"visibility\":\"PUBLIC\"}").andExpect(status().isOk());
        jdbc.update("update collections set published_at=? where id=?", "2026-01-03 12:00:00", collection);
        change(authorSession, collectionApi, "{\"title\":\"Public itinerary\"}").andExpect(status().isOk());
        change(authorSession, reviewApi, "{\"body\":\"Updated review text\"}").andExpect(status().isOk());
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].target.title").value("Public itinerary"))
                .andExpect(jsonPath("$.items[0].eventAt").value("2026-01-03T12:00:00Z"))
                .andExpect(jsonPath("$.items[1].eventAt").value("2026-01-02T12:30:00.123456Z"))
                .andExpect(jsonPath("$.items[1].target.excerpt").value("Updated review text"));
        mvc.perform(get("/feed").session(readerSession))
                .andExpect(content().string(containsString("href=\"/collections/" + collection + "\"")));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/reviews/" + review + "\"")))
                .andExpect(content().string(containsString("href=\"/collections/" + collection + "\"")))
                .andExpect(content().string(containsString("Updated review text")));

        change(authorSession, collectionApi, "{\"visibility\":\"PRIVATE\"}").andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(content().string(not(containsString("Public itinerary"))));
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(content().string(not(containsString("Public itinerary"))));
        change(authorSession, collectionApi, "{\"visibility\":\"PUBLIC\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select published_at > '2026-01-03 12:00:00' from collections where id=?",
                Boolean.class, collection)).isTrue();
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].eventAt").value(not("2026-01-03T12:00:00Z")));
        mvc.perform(delete(collectionApi).session(authorSession).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/v1/users/{id}/follow", author).session(readerSession).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/users/{id}/follow", author).session(readerSession).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete(reviewApi).session(authorSession).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(API).session(readerSession)).andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(provider);
    }

    @Test
    void webPagesHaveBoundedNavigationAndRecoverFromStalePageNumbers() throws Exception {
        follow(reader, author);
        jdbc.batchUpdate("""
                insert into collections (owner_id,title,description,visibility,published_at,created_at,updated_at)
                values (?,?,'','PUBLIC',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, IntStream.range(0, 21).mapToObj(i -> new Object[]{author, "Guide " + i, EVENT_TIME}).toList());
        mvc.perform(get("/feed").with(user(Long.toString(reader)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/feed?page=1\"")))
                .andExpect(content().string(containsString("Page 1 of 2")));
        mvc.perform(get("/feed").with(user(Long.toString(reader))).param("page", "1"))
                .andExpect(content().string(containsString("href=\"/feed?page=0\"")))
                .andExpect(content().string(containsString("Page 2 of 2")));
        mvc.perform(get("/feed").with(user(Long.toString(reader))).param("page", "2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Go to the first page")));
    }

    @Test
    void openApiDescribesTheAuthenticatedFeed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feed'].get.security[0].sessionCookie").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feed'].get.responses['200'].content.*.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feed'].get.responses['401'].content['application/problem+json'].schema").exists());
    }

    private ResultActions feed(int page, int size) throws Exception {
        return mvc.perform(get(API).with(user(Long.toString(reader)))
                .param("page", Integer.toString(page)).param("size", Integer.toString(size)));
    }

    private ResultActions change(MockHttpSession session, String path, String payload) throws Exception {
        return mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content(payload));
    }

    private MockHttpSession signIn(String username) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", PASSWORD))
                .andExpect(redirectedUrl("/explore")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private void follow(long viewer, long target) {
        jdbc.update("insert into follows (follower_id,followed_id,created_at) values (?,?,UTC_TIMESTAMP(6))", viewer, target);
    }

    private long userId(String username) {
        return jdbc.queryForObject("select id from users where username=?", Long.class, username);
    }

    private long insertReview(long owner, long place, String body, String time) {
        jdbc.update("""
                insert into reviews (author_id,place_id,rating,body,created_at,updated_at)
                values (?,?,4,?,?,UTC_TIMESTAMP(6))
                """, owner, place, body, time);
        return jdbc.queryForObject("select id from reviews where author_id=? and place_id=?", Long.class, owner, place);
    }

    private long insertCollection(long owner, String title, String time) {
        jdbc.update("""
                insert into collections (owner_id,title,description,visibility,published_at,created_at,updated_at)
                values (?,?,'',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, owner, title, time == null ? "PRIVATE" : "PUBLIC", time);
        return jdbc.queryForObject("select id from collections where owner_id=? and title=?", Long.class, owner, title);
    }
}
