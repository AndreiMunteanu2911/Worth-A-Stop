package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.ReviewInteractionService;
import com.locationreview.location_review_platform.service.ReviewService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewInteractionIntegrationTests {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final String REVIEW = "/api/v1/reviews/{id}";
    private static final String LIKES = REVIEW + "/likes";
    private static final String COMMENTS = REVIEW + "/comments";
    private static final String COMMENT = "/api/v1/comments/{id}";
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
    @Autowired ReviewInteractionService interactions;
    @Autowired ReviewService reviews;
    @Autowired PasswordEncoder encoder;
    private long author, reader, place, review;

    @BeforeEach
    void setup() {
        jdbc.update("delete from reviews");
        jdbc.update("delete from places");
        jdbc.update("delete from users");
        String hash = encoder.encode(PASSWORD);
        jdbc.update("""
                insert into users (username,email,password_hash,display_name,biography,created_at,updated_at)
                values ('author','author@example.test',?,'Author','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       ('reader','reader@example.test',?,'Reader','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, hash, hash);
        author = jdbc.queryForObject("select id from users where username='author'", Long.class);
        reader = jdbc.queryForObject("select id from users where username='reader'", Long.class);
        jdbc.update("""
                insert into places (google_place_id,source,created_at,updated_at)
                values ('ChIJ_interactions_test','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        place = jdbc.queryForObject("select id from places", Long.class);
        insertReview(author, "A helpful review");
        review = jdbc.queryForObject("select id from reviews", Long.class);
    }

    @Test
    void likeAndUnlikeAreIdempotentAndAllReviewViewsUseTheViewer() throws Exception {
        mvc.perform(get(REVIEW, review)).andExpect(jsonPath("$.likeCount").value(0));
        like(reader, review).andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        LocalDateTime createdAt = jdbc.queryForObject("select created_at from review_likes", LocalDateTime.class);
        like(reader, review).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1));
        assertThat(jdbc.queryForObject("select created_at from review_likes", LocalDateTime.class)).isEqualTo(createdAt);
        mvc.perform(get(REVIEW, review)).andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(false));
        mvc.perform(get(REVIEW, review).with(user(Long.toString(reader))))
                .andExpect(jsonPath("$.likedByMe").value(true));
        insertReview(reader, "Another helpful review");
        mvc.perform(get("/api/v1/places/{id}/reviews", place).with(user(Long.toString(reader))))
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.items[1].likeCount").value(1))
                .andExpect(jsonPath("$.items[1].likedByMe").value(true));
        mvc.perform(get("/api/v1/users/{id}/reviews", author).with(user(Long.toString(reader))))
                .andExpect(jsonPath("$.items[0].likedByMe").value(true));

        like(author, review).andExpect(jsonPath("$.likeCount").value(2));
        mvc.perform(patch(REVIEW, review).with(user(Long.toString(author))).with(csrf())
                        .contentType("application/json").content("{\"rating\":3}"))
                .andExpect(jsonPath("$.likeCount").value(2)).andExpect(jsonPath("$.likedByMe").value(true));
        unlike(reader, review).andExpect(status().isNoContent());
        unlike(reader, review).andExpect(status().isNoContent());
        mvc.perform(get(REVIEW, review).with(user(Long.toString(author))))
                .andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    void commentsAreTrimmedPublicAndOnlyDeletableByTheCommentAuthor() throws Exception {
        mvc.perform(get(COMMENTS, review)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        var created = comment(reader, review, "{\"body\":\"  Helpful tip!  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Helpful tip!"))
                .andExpect(jsonPath("$.reviewId").value(review))
                .andExpect(jsonPath("$.author.id").value(reader))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.passwordHash").doesNotExist()).andReturn();
        long commentId = jdbc.queryForObject("select id from review_comments", Long.class);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/comments/" + commentId);
        mvc.perform(get(COMMENTS, review)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(commentId));
        mvc.perform(delete(COMMENT, commentId).with(user(Long.toString(author))).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(delete(COMMENT, commentId).with(user(Long.toString(reader))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(COMMENT, commentId).with(user(Long.toString(reader))).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("comment_not_found"));
        mvc.perform(get(COMMENTS, review)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void commentsPaginateByTimeAndId() throws Exception {
        for (String message : List.of("First", "Second", "Third")) {
            comment(reader, review, "{\"body\":\"" + message + "\"}").andExpect(status().isCreated());
        }
        jdbc.update("update review_comments set created_at='2026-01-02 00:00:00'");
        jdbc.update("update review_comments set created_at='2026-01-01 00:00:00' where body='Third'");
        mvc.perform(get(COMMENTS, review).param("size", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].body").value("Third"))
                .andExpect(jsonPath("$.items[1].body").value("First"));
        mvc.perform(get(COMMENTS, review).param("page", "1").param("size", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].body").value("Second"));
    }

    @Test
    void invalidInputsAndMissingTargetsReturnProblemDetails() throws Exception {
        for (String body : List.of("{}", "{\"body\":null}", "{\"body\":123}", "{\"body\":\"   \"}",
                "{\"body\":\"" + "a".repeat(1001) + "\"}")) {
            comment(reader, review, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.body").exists());
        }
        comment(reader, review, "{\"body\":\"" + "😀".repeat(1000) + "\"}").andExpect(status().isCreated());
        mvc.perform(get(COMMENTS, review).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get(COMMENTS, review).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(COMMENTS, review).param("page", "2147483647")).andExpect(status().isBadRequest());
        mvc.perform(get(COMMENTS, Long.MAX_VALUE)).andExpect(status().isNotFound());
        comment(reader, Long.MAX_VALUE, "{\"body\":\"Hi\"}").andExpect(status().isNotFound());
        like(reader, Long.MAX_VALUE).andExpect(status().isNotFound());
        unlike(reader, Long.MAX_VALUE).andExpect(status().isNotFound());
        like(reader, 0).andExpect(status().isBadRequest());
        mvc.perform(delete(COMMENT, 0).with(user(Long.toString(reader))).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void allMutationsRequireSessionAndCsrf() throws Exception {
        String body = "{\"body\":\"Hello\"}";
        mvc.perform(post(LIKES, review).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(LIKES, review).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(COMMENTS, review).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(COMMENT, 1).with(csrf())).andExpect(status().isUnauthorized());
        var signedIn = user(Long.toString(reader));
        mvc.perform(post(LIKES, review).with(signedIn)).andExpect(status().isForbidden());
        mvc.perform(delete(LIKES, review).with(signedIn)).andExpect(status().isForbidden());
        mvc.perform(post(COMMENTS, review).with(signedIn).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete(COMMENT, 1).with(signedIn)).andExpect(status().isForbidden());
        mvc.perform(post("/reviews/{id}/like", review).param("liked", "true").with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/reviews/{id}/comments", review).with(signedIn).param("body", "Hello")).andExpect(status().isForbidden());
        mvc.perform(post("/comments/{id}/delete", 1).with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void twoAccountsUseTheDiscussionAndControlTheirOwnComments() throws Exception {
        MockHttpSession authorSession = signIn("author");
        MockHttpSession readerSession = signIn("reader");
        String discussion = "/reviews/" + review;
        mvc.perform(get("/users/{id}", author)).andExpect(content().string(containsString("href=\"" + discussion + "\"")));
        mvc.perform(get(discussion)).andExpect(status().isOk()).andExpect(content().string(containsString("No comments yet.")))
                .andExpect(content().string(containsString("Log in to comment")))
                .andExpect(content().string(not(containsString("id=\"comment-form\""))));
        mvc.perform(get(discussion).session(readerSession)).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post(discussion + "/like").session(readerSession).with(csrf()).param("liked", "true"))
                .andExpect(redirectedUrl(discussion + "?page=0#review-likes"));
        mvc.perform(get(discussion).session(readerSession)).andExpect(content().string(containsString("Unlike")))
                .andExpect(content().string(containsString("1 like")));
        mvc.perform(get(discussion).session(authorSession)).andExpect(content().string(not(containsString("Unlike"))));
        mvc.perform(post(discussion + "/comments").session(readerSession).with(csrf()).param("body", "x".repeat(1001)))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("comment-error")))
                .andExpect(content().string(containsString("x".repeat(1001))));
        var result = mvc.perform(post(discussion + "/comments").session(readerSession).with(csrf())
                        .param("body", "<script>literal</script>"))
                .andExpect(redirectedUrl(discussion + "?page=0#comments")).andReturn();
        String remove = "/comments/" + jdbc.queryForObject("select id from review_comments", Long.class) + "/delete";
        mvc.perform(get(discussion).session(readerSession).flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("Comment posted.")))
                .andExpect(content().string(containsString("&lt;script&gt;literal&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>literal</script>"))))
                .andExpect(content().string(containsString("action=\"" + remove + "\"")));
        mvc.perform(get(discussion).session(authorSession)).andExpect(content().string(not(containsString(remove))));
        mvc.perform(post(remove).session(authorSession).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(remove).session(readerSession).with(csrf())).andExpect(redirectedUrl(discussion + "?page=0#comments"));
        mvc.perform(post(discussion + "/like").session(readerSession).with(csrf()).param("liked", "false"))
                .andExpect(redirectedUrl(discussion + "?page=0#review-likes"));
        mvc.perform(get(discussion)).andExpect(content().string(containsString("0 likes")));
    }

    @Test
    void postingGoesToLastPageAndDeletingItsLastCommentReturnsToPreviousPage() throws Exception {
        jdbc.batchUpdate("""
                insert into review_comments (review_id,author_id,body,created_at,updated_at)
                values (?,?,'Earlier comment','2026-01-01 00:00:00','2026-01-01 00:00:00')
                """, IntStream.range(0, 20).mapToObj(i -> new Object[]{review, author}).toList());
        String discussion = "/reviews/" + review;
        mvc.perform(post(discussion + "/comments").with(user(Long.toString(reader))).with(csrf()).param("body", "Latest comment"))
                .andExpect(redirectedUrl(discussion + "?page=1#comments"));
        mvc.perform(get(discussion)).andExpect(content().string(containsString(discussion + "?page=1#comments")));
        mvc.perform(get(discussion).param("page", "1")).andExpect(content().string(containsString("Latest comment")))
                .andExpect(content().string(containsString("Page 2 of 2")));
        long id = jdbc.queryForObject("select id from review_comments where body='Latest comment'", Long.class);
        mvc.perform(post("/comments/{id}/delete", id).with(user(Long.toString(reader))).with(csrf()).param("page", "1"))
                .andExpect(redirectedUrl(discussion + "?page=0#comments"));
    }

    @Test
    void concurrentLikesConvergeAndDeletingTheReviewCascades() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var futures = IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return interactions.like(reader, review);
            })).toList();
            for (var future : futures) {
                assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(new ReviewInteractionService.LikeState(true, 1));
            }
        }
        comment(reader, review, "{\"body\":\"Thanks\"}").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from review_likes", Long.class)).isEqualTo(1);
        mvc.perform(delete(REVIEW, review).with(user(Long.toString(author))).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from review_likes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from review_comments", Long.class)).isZero();
        mvc.perform(get("/reviews/{id}", review)).andExpect(status().isNotFound());
    }

    @Test
    void concurrentCommentAndReviewDeletionCannotLeaveAnOrphan() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var comment = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    return interactions.addComment(reader, review, "Thanks").reviewId();
                } catch (AccountException ex) {
                    assertThat(ex.code()).isEqualTo("review_not_found");
                    return 0L;
                }
            });
            var deletion = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                reviews.delete(author, review);
                return null;
            });
            assertThat(comment.get(15, TimeUnit.SECONDS)).isIn(0L, review);
            deletion.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from reviews", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from review_comments", Long.class)).isZero();
    }

    @Test
    void openApiDocumentsAllInteractionOperations() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reviews/{id}/likes'].post.security[0].sessionCookie").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reviews/{id}/likes'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reviews/{id}/comments'].get.responses['200'].content.*.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reviews/{id}/comments'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/comments/{id}'].delete.responses['403'].content['application/problem+json'].schema").exists())
                .andExpect(jsonPath("$.components.schemas.CommentCreateRequest.properties.body.type").value("string"));
    }

    private void insertReview(long userId, String body) {
        jdbc.update("""
                insert into reviews (author_id,place_id,rating,body,created_at,updated_at)
                values (?,?,4,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, userId, place, body);
    }

    private ResultActions like(long viewer, long id) throws Exception {
        return mvc.perform(post(LIKES, id).with(user(Long.toString(viewer))).with(csrf()));
    }

    private ResultActions unlike(long viewer, long id) throws Exception {
        return mvc.perform(delete(LIKES, id).with(user(Long.toString(viewer))).with(csrf()));
    }

    private ResultActions comment(long viewer, long id, String body) throws Exception {
        return mvc.perform(post(COMMENTS, id).with(user(Long.toString(viewer))).with(csrf())
                .contentType("application/json").content(body));
    }

    private MockHttpSession signIn(String username) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", PASSWORD))
                .andExpect(redirectedUrl("/explore")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
