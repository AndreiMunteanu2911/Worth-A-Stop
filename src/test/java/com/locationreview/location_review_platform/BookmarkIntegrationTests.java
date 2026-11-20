package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.places.GooglePlace;
import com.locationreview.location_review_platform.places.GooglePlacesClient;
import com.locationreview.location_review_platform.places.PlaceProviderException;
import com.locationreview.location_review_platform.service.BookmarkService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookmarkIntegrationTests {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final String API = "/api/v1/bookmarks";
    private static final String GOOGLE_ID = "ChIJ_bookmark_test";
    static { MYSQL.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookmarkService bookmarks;
    @MockitoBean GooglePlacesClient provider;
    private long owner, other, place, legacy;

    @BeforeEach
    void setup() {
        jdbc.update("delete from bookmarks");
        jdbc.update("delete from collections");
        jdbc.update("delete from places");
        jdbc.update("delete from users");
        jdbc.update("""
                insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                values ('owner','owner@example.test','hash','Owner','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('other','other@example.test','hash','Other','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        owner = jdbc.queryForObject("select id from users where username='owner'", Long.class);
        other = jdbc.queryForObject("select id from users where username='other'", Long.class);
        jdbc.update("insert into places (google_place_id,source,created_at,updated_at) values (?,'GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", GOOGLE_ID);
        jdbc.update("""
                insert into places (osm_type,osm_id,name,category,osm_tag_value,latitude,longitude,address,source,
                                    metadata_fetched_at,created_at,updated_at)
                values ('NODE',123,'Old Park','PARK','park',44,26,'Park Street','OSM',
                        UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        place = jdbc.queryForObject("select id from places where google_place_id=?", Long.class, GOOGLE_ID);
        legacy = jdbc.queryForObject("select id from places where osm_id=123", Long.class);
    }

    @Test
    void saveAndRemoveArePrivateIdempotentAndIndependentOfCollections() throws Exception {
        save(owner, place).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.placeId").value(place));
        LocalDateTime created = jdbc.queryForObject("select created_at from bookmarks", LocalDateTime.class);
        save(owner, place).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select created_at from bookmarks", LocalDateTime.class)).isEqualTo(created);
        assertThat(jdbc.queryForObject("select count(*) from collection_places", Long.class)).isZero();
        jdbc.update("""
                insert into collections (owner_id,title,description,visibility,created_at,updated_at)
                values (?,'Guide','','PRIVATE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, owner);
        jdbc.update("""
                insert into collection_places (collection_id,place_id,position,added_at)
                select id,?,0,UTC_TIMESTAMP(6) from collections
                """, place);

        mvc.perform(get(API).with(user("" + other))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        remove(other, place).andExpect(status().isNoContent());
        mvc.perform(get(API).with(user("" + owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].place.id").value(place))
                .andExpect(jsonPath("$.items[0].place.source").value("GOOGLE"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].user").doesNotExist());
        save(other, place).andExpect(status().isOk());
        remove(owner, place).andExpect(status().isNoContent());
        remove(owner, place).andExpect(status().isNoContent());
        assertThat(bookmarks.isSaved(other, place)).isTrue();
        assertThat(bookmarks.isSaved(owner, place)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from places", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select place_id from collection_places", Long.class)).isEqualTo(place);
        verifyNoInteractions(provider);
    }

    @Test
    void validatesTargetsPaginationAuthAndCsrf() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
        mvc.perform(get("/saved")).andExpect(redirectedUrl("/login"));
        mvc.perform(post(API).with(csrf()).contentType("application/json").content(body(place)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(API + "/{id}", place).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(API).with(user("" + owner)).contentType("application/json").content(body(place)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(API + "/{id}", place).with(user("" + owner))).andExpect(status().isForbidden());
        mvc.perform(post("/saved/{id}/remove", place).with(user("" + owner))).andExpect(status().isForbidden());
        for (String invalid : List.of("{}", body(0), "{\"placeId\":1.5}", "{\"placeId\":9223372036854775808}")) {
            mvc.perform(post(API).with(user("" + owner)).with(csrf()).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.placeId").isNotEmpty());
        }
        for (String invalid : List.of("0", "51", "bad")) {
            mvc.perform(get(API).with(user("" + owner)).param("size", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(API).with(user("" + owner)).param("page", "-1")).andExpect(status().isBadRequest());
        remove(owner, 0).andExpect(status().isBadRequest());
        save(owner, Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("place_not_found"));
        remove(owner, Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("place_not_found"));
    }

    @Test
    void newestFirstUsesIdAsTiebreakerAndPaginates() throws Exception {
        bookmarks.save(owner, legacy);
        bookmarks.save(owner, place);
        jdbc.update("update bookmarks set created_at='2026-01-01 12:00:00'");
        mvc.perform(get(API).with(user("" + owner)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].place.id").value(place));
        mvc.perform(get(API).with(user("" + owner)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].place.name").value("Old Park"))
                .andExpect(jsonPath("$.items[0].place.address").value("Park Street"));
        jdbc.update("update bookmarks set created_at='2026-01-02 12:00:00' where place_id=?", legacy);
        mvc.perform(get(API).with(user("" + owner)).param("size", "1"))
                .andExpect(jsonPath("$.items[0].place.id").value(legacy));
        verifyNoInteractions(provider);
    }

    @Test
    void remoteResolutionSaveReloadAndHtmlRemoval() throws Exception {
        String remoteId = "ChIJ_bookmark_remote";
        when(provider.details(remoteId)).thenReturn(new GooglePlace(remoteId, "Remote Park", "park", List.of("park"),
                BigDecimal.valueOf(44), BigDecimal.valueOf(26), null, null, List.of()));
        mvc.perform(get("/places/google/{id}", remoteId).with(user("" + owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-bookmarked=\"false\"")));
        mvc.perform(post("/api/v1/places/resolve").with(user("" + owner)).with(csrf())
                .contentType("application/json").content("{\"googlePlaceId\":\"" + remoteId + "\"}"))
                .andExpect(status().isOk());
        long resolved = jdbc.queryForObject("select id from places where google_place_id=?", Long.class, remoteId);
        assertThat(bookmarks.isSaved(owner, resolved)).isFalse();
        save(owner, resolved).andExpect(status().isOk());
        when(provider.details(remoteId)).thenThrow(new PlaceProviderException(503, "Unavailable"));
        mvc.perform(get("/api/v1/places/{id}", resolved)).andExpect(jsonPath("$.metadataUnavailable").value(true));
        mvc.perform(get("/places/{id}", resolved).with(user("" + owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-bookmarked=\"true\"")));
        mvc.perform(get("/places/google/{id}", remoteId).with(user("" + owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-bookmarked=\"true\"")));
        mvc.perform(get("/places/{id}", resolved).with(user("" + other)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-bookmarked=\"false\"")));
        mvc.perform(get("/saved").with(user("" + owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("href=\"/places/" + resolved + "\"")));
        mvc.perform(get("/saved").with(user("" + other))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/places/" + resolved + "\""))));
        var removal = mvc.perform(post("/saved/{id}/remove", resolved).with(user("" + owner)).with(csrf()))
                .andExpect(redirectedUrl("/saved?page=0"))
                .andExpect(flash().attribute("notice", "Place removed from your saved list.")).andReturn();
        mvc.perform(get("/saved").with(user("" + owner)).flashAttrs(removal.getFlashMap()))
                .andExpect(content().string(containsString("Place removed from your saved list.")))
                .andExpect(content().string(containsString("Explore places")));
    }

    @Test
    void concurrentWritesPreserveOneBookmarkAndBothCallsSucceed() throws Exception {
        runTogether(() -> bookmarks.save(owner, place));
        assertThat(jdbc.queryForObject("select count(*) from bookmarks", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                insert into bookmarks (user_id,place_id,created_at) values (?,?,UTC_TIMESTAMP(6))
                """, owner, place)).isInstanceOf(DuplicateKeyException.class);
        runTogether(() -> bookmarks.remove(owner, place));
        assertThat(jdbc.queryForObject("select count(*) from bookmarks", Long.class)).isZero();
    }

    @Test
    void savedPageReturnsToLastAvailablePageAfterRemoval() throws Exception {
        jdbc.batchUpdate("""
                insert into places (google_place_id,source,created_at,updated_at)
                values (?,'GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, IntStream.range(0, 19).mapToObj(index -> new Object[]{"ChIJ_saved_" + index}).toList());
        jdbc.update("""
                insert into bookmarks (user_id,place_id,created_at)
                select ?,id,'2026-01-01 12:00:00' from places
                """, owner);
        long oldest = jdbc.queryForObject("select min(id) from places", Long.class);
        mvc.perform(get("/saved").with(user("" + owner)))
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("/saved?page=1")));
        mvc.perform(get("/saved").with(user("" + owner)).param("page", "1"))
                .andExpect(content().string(containsString("/saved/" + oldest + "/remove")));
        var removal = mvc.perform(post("/saved/{id}/remove", oldest).param("page", "1")
                .with(user("" + owner)).with(csrf())).andExpect(redirectedUrl("/saved?page=1")).andReturn();
        mvc.perform(get("/saved").with(user("" + owner)).param("page", "1").flashAttrs(removal.getFlashMap()))
                .andExpect(redirectedUrl("/saved?page=0")).andExpect(flash().attributeExists("notice"));
    }

    @Test
    void openApiDocumentsEveryBookmarkOperation() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks'].post.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks/{placeId}'].delete.responses.204").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks'].get.responses['401'].content['application/problem+json'].schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks/{placeId}'].delete.responses['404'].content['application/problem+json'].schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookmarks'].get.security[0].sessionCookie").exists());
    }

    private ResultActions save(long userId, long placeId) throws Exception {
        return mvc.perform(post(API).with(user("" + userId)).with(csrf())
                .contentType("application/json").content(body(placeId)));
    }

    private ResultActions remove(long userId, long placeId) throws Exception {
        return mvc.perform(delete(API + "/{id}", placeId).with(user("" + userId)).with(csrf()));
    }

    private static String body(long placeId) {
        return "{\"placeId\":" + placeId + "}";
    }

    private static void runTogether(Runnable action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Void> write = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                action.run();
                return null;
            };
            var first = executor.submit(write);
            var second = executor.submit(write);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }
}
