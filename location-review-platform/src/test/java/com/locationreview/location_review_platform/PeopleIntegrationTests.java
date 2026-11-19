package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.service.PeopleService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PeopleIntegrationTests {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final String USERS = "/api/v1/users";
    private static final String FOLLOW = USERS + "/{id}/follow";
    private static final String PASSWORD = "integration-password";
    static { MYSQL.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PeopleService people;
    @Autowired PasswordEncoder encoder;

    private long alice, bob, carol;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from follows");
        jdbc.update("delete from users");
        String hash = encoder.encode(PASSWORD);
        jdbc.update("""
                insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                values ('alice','alice@example.test',?,'Alice','<script>profile</script>',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('bob_reader','bob@example.test',?,'Bob','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('carol','carol@example.test',?,'Carol Reader','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, hash, hash, hash);
        alice = id("alice");
        bob = id("bob_reader");
        carol = id("carol");
    }

    @Test
    void publicSearchMatchesBothFieldsLiterallyAndPaginates() throws Exception {
        follow(alice, bob).andExpect(status().isOk());
        mvc.perform(get(USERS).param("q", "  READER  ").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(bob))
                .andExpect(jsonPath("$.items[0].followerCount").value(1))
                .andExpect(jsonPath("$.items[0].followingCount").value(0))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get(USERS).param("q", "Reader").param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.items[0].id").value(carol));
        mvc.perform(get(USERS).param("q", "_reader"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bob));
        mvc.perform(get(USERS).param("q", "%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(USERS).param("q", "example.test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void followAndUnfollowAreIdempotentAndScopedToTheCaller() throws Exception {
        follow(alice, bob).andExpect(status().isOk()).andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        LocalDateTime created = jdbc.queryForObject("select created_at from follows", LocalDateTime.class);
        follow(alice, bob).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(1));
        assertThat(jdbc.queryForObject("select created_at from follows", LocalDateTime.class)).isEqualTo(created);
        follow(carol, bob).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(2));
        unfollow(alice, bob).andExpect(status().isNoContent());
        unfollow(alice, bob).andExpect(status().isNoContent());
        mvc.perform(get(USERS + "/{id}", bob)).andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(get(USERS + "/me").with(user(Long.toString(alice))))
                .andExpect(jsonPath("$.followingCount").value(0));
        mvc.perform(get(USERS + "/{id}", carol)).andExpect(jsonPath("$.followingCount").value(1));
    }

    @Test
    void listsUseRelationshipTimestampAndIdInBothDirections() throws Exception {
        follow(alice, bob).andExpect(status().isOk());
        follow(carol, bob).andExpect(status().isOk());
        follow(alice, carol).andExpect(status().isOk());
        jdbc.update("update follows set created_at='2026-01-01 12:00:00'");
        assertConnections(bob, "followers", carol, alice);
        assertConnections(alice, "following", carol, bob);
        jdbc.update("update follows set created_at='2026-01-02 12:00:00' where follower_id=? and followed_id=?", alice, bob);
        assertConnections(bob, "followers", alice, carol);
        assertConnections(alice, "following", bob, carol);
        mvc.perform(get(USERS + "/{id}/following", bob)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void validatesQueriesTargetsAuthenticationAndCsrf() throws Exception {
        mvc.perform(get(USERS)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request"));
        for (String q : List.of(" ", "a", "x".repeat(81))) {
            mvc.perform(get(USERS).param("q", q)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.q").exists());
        }
        mvc.perform(get(USERS).param("q", "al").param("page", "-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_pagination"));
        mvc.perform(get(USERS).param("q", "al").param("page", "2147483647")).andExpect(status().isBadRequest());
        mvc.perform(get(USERS + "/{id}/followers", bob).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get(USERS + "/{id}/following", alice).param("size", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get(USERS + "/{id}/followers", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get(USERS + "/{id}/following", 0)).andExpect(status().isBadRequest());
        follow(alice, Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("user_not_found"));
        unfollow(alice, Long.MAX_VALUE).andExpect(status().isNotFound());
        follow(alice, 0).andExpect(status().isBadRequest());
        follow(alice, alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self_follow"));
        unfollow(alice, alice).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self_follow"));
        mvc.perform(post(FOLLOW, bob).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(FOLLOW, bob).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(FOLLOW, bob).with(user(Long.toString(alice)))).andExpect(status().isForbidden());
        mvc.perform(delete(FOLLOW, bob).with(user(Long.toString(alice)))).andExpect(status().isForbidden());
        mvc.perform(post("/users/{id}/follow", bob).with(csrf()).param("following", "true"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/users/{id}/follow", bob).with(user(Long.toString(alice))).param("following", "true"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from follows", Long.class)).isZero();
    }

    @Test
    void twoSignedInUsersCanDiscoverAndFollowThroughTheWebsite() throws Exception {
        MockHttpSession aliceSession = signIn("alice");
        MockHttpSession bobSession = signIn("bob_reader");

        mvc.perform(get("/people")).andExpect(status().isOk()).andExpect(content().string(containsString("aria-current=\"page\"")));
        mvc.perform(get("/people").param("q", "al"))
                .andExpect(content().string(containsString("/users/" + alice)))
                .andExpect(content().string(containsString("&lt;script&gt;profile&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("alice@example.test"))));
        mvc.perform(get("/people").param("q", "unknown")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No people match")));
        mvc.perform(get("/people").param("q", "x")).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("people-search-error")));
        mvc.perform(get("/users/{id}", bob)).andExpect(content().string(containsString("Log in to follow")))
                .andExpect(content().string(not(containsString("profile-follow"))));
        mvc.perform(get("/users/{id}", bob).session(bobSession))
                .andExpect(content().string(not(containsString("profile-follow"))));
        mvc.perform(get("/users/{id}", bob).session(aliceSession))
                .andExpect(content().string(containsString("name=\"following\" value=\"true\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        var followed = mvc.perform(post("/users/{id}/follow", bob).session(aliceSession).with(csrf())
                        .param("following", "true"))
                .andExpect(redirectedUrl("/users/" + bob)).andExpect(flash().attributeExists("followNotice")).andReturn();
        mvc.perform(get("/users/{id}", bob).session(aliceSession).flashAttrs(followed.getFlashMap()))
                .andExpect(content().string(containsString("Unfollow"))).andExpect(content().string(containsString("1 follower")))
                .andExpect(content().string(containsString("now following this person")));
        mvc.perform(get("/users/{id}/followers", bob).session(bobSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("@alice")));
        mvc.perform(get("/users/{id}/following", alice))
                .andExpect(status().isOk()).andExpect(content().string(containsString("@bob_reader")));
        mvc.perform(post("/users/{id}/follow", bob).session(aliceSession).with(csrf()).param("following", "false"))
                .andExpect(redirectedUrl("/users/" + bob)).andExpect(flash().attributeExists("followNotice"));
        mvc.perform(get("/users/{id}/followers", bob).session(bobSession))
                .andExpect(content().string(containsString("No followers yet.")));
    }

    @Test
    void websitePaginationKeepsSearchAndRelationshipContext() throws Exception {
        jdbc.batchUpdate("""
                insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                values (?,?,'hash','Local Reader','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, IntStream.range(0, 21).mapToObj(i -> new Object[]{"local_" + i, i + "@example.test"}).toList());
        jdbc.update("""
                insert into follows (follower_id,followed_id,created_at)
                select id,?,'2026-01-01 12:00:00' from users where display_name='Local Reader'
                """, bob);
        mvc.perform(get("/people").param("q", "Local Reader")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/people?q=Local%20Reader&amp;page=1")));
        mvc.perform(get("/users/{id}/followers", bob)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/users/" + bob + "/followers?page=1")));
        mvc.perform(get("/users/{id}/followers", bob).param("page", "1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Page 2 of 2")))
                .andExpect(content().string(containsString("/users/" + bob + "/followers?page=0")));
    }

    @Test
    void concurrentDuplicateFollowsBothReturnThePersistedState() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var futures = IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return people.follow(alice, bob);
            })).toList();
            for (var future : futures) {
                assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(new PeopleService.FollowState(true, 1));
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from follows", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into follows (follower_id,followed_id,created_at) values (?,?,UTC_TIMESTAMP(6))",
                alice, bob)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void openApiIncludesAllPeopleOperationsAndSchemas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users'].get.responses['200'].content.*.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/followers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/following'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/follow'].post.security[0].sessionCookie").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/follow'].post.responses['409'].content['application/problem+json'].schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/follow'].delete.responses['204']").exists());
    }

    private void assertConnections(long userId, String direction, long first, long second) throws Exception {
        String path = USERS + "/{id}/" + direction;
        mvc.perform(get(path, userId).param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
        mvc.perform(get(path, userId).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second));
    }

    private long id(String username) {
        return jdbc.queryForObject("select id from users where username=?", Long.class, username);
    }

    private ResultActions follow(long viewer, long target) throws Exception {
        return mvc.perform(post(FOLLOW, target).with(user(Long.toString(viewer))).with(csrf()));
    }

    private ResultActions unfollow(long viewer, long target) throws Exception {
        return mvc.perform(delete(FOLLOW, target).with(user(Long.toString(viewer))).with(csrf()));
    }

    private MockHttpSession signIn(String username) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", PASSWORD))
                .andExpect(redirectedUrl("/explore")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
