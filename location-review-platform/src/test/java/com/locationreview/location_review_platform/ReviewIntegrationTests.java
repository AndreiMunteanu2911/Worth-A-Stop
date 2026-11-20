package com.locationreview.location_review_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.locationreview.location_review_platform.places.GooglePlacesClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest @AutoConfigureMockMvc
class ReviewIntegrationTests {
    static final MySQLContainer mysql=new MySQLContainer("mysql:8.4");
    static { mysql.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { r.add("spring.datasource.url",mysql::getJdbcUrl); r.add("spring.datasource.username",mysql::getUsername); r.add("spring.datasource.password",mysql::getPassword); }
    @MockitoBean GooglePlacesClient provider;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    long author,other,place;
    @BeforeEach void setup() {
        jdbc.update("delete from reviews"); jdbc.update("delete from places"); jdbc.update("delete from users");
        jdbc.update("insert into users(username,email,password_hash,display_name,biography,created_at,updated_at) values ('author','author@example.test','hash','Author','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),('other','other@example.test','hash','Other','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        author=jdbc.queryForObject("select id from users where username='author'",Long.class); other=jdbc.queryForObject("select id from users where username='other'",Long.class);
        jdbc.update("insert into places(google_place_id,source,created_at,updated_at) values ('ChIJ_test_review','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))"); place=jdbc.queryForObject("select id from places where google_place_id='ChIJ_test_review'",Long.class);
    }
    @Test void lifecycleAuthorizationAndAggregates() throws Exception {
        String payload="{\"placeId\":"+place+",\"rating\":4,\"body\":\"A helpful review\",\"visitDate\":\"2020-01-01\"}";
        mvc.perform(get("/api/v1/places/{id}/rating-summary",place)).andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(0)).andExpect(jsonPath("$.average").isEmpty()).andExpect(jsonPath("$.ratingCounts.4").value(0));
        mvc.perform(post("/api/v1/reviews").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reviews").with(user(""+author)).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reviews").with(user(""+author)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated()).andExpect(header().exists("Location"));
        long review=jdbc.queryForObject("select id from reviews",Long.class);
        mvc.perform(post("/api/v1/reviews").with(user(""+author)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/reviews/{id}",review)).andExpect(status().isOk()).andExpect(jsonPath("$.author.username").value("author"));
        mvc.perform(get("/api/v1/places/{id}/reviews",place)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/users/{id}/reviews",author)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(review));
        mvc.perform(patch("/api/v1/reviews/{id}",review).with(user(""+other)).with(csrf()).contentType("application/json").content("{\"rating\":2}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/reviews/{id}",review).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"rating\":2,\"visitDate\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.visitDate").isEmpty());
        mvc.perform(get("/api/v1/places/{id}/rating-summary",place)).andExpect(jsonPath("$.average").value(2.0)).andExpect(jsonPath("$.ratingCounts.2").value(1));
        mvc.perform(delete("/api/v1/reviews/{id}",review).with(user(""+other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/reviews/{id}",review).with(user(""+author)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/places/{id}/rating-summary",place)).andExpect(jsonPath("$.reviewCount").value(0));
        mvc.perform(get("/api/v1/reviews/{id}",review)).andExpect(status().isNotFound());
    }
    @Test void paginationSortingAndDependentDeletion() throws Exception {
        jdbc.update("insert into reviews(author_id,place_id,rating,body,created_at,updated_at) values (?,?,5,'Excellent review',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),(?,?,1,'Disappointing review',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",author,place,other,place);
        long review=jdbc.queryForObject("select id from reviews where author_id=?",Long.class,author);
        jdbc.update("insert into review_likes(review_id,user_id,created_at) values (?,?,UTC_TIMESTAMP(6))",review,other);
        jdbc.update("insert into review_comments(review_id,author_id,body,created_at,updated_at) values (?,?,'Comment',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",review,other);
        mvc.perform(get("/api/v1/places/{id}/reviews",place).param("sort","rating_low").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].rating").value(1));
        mvc.perform(get("/api/v1/places/{id}/reviews",place).param("sort","rating_low").param("size","1").param("page","1")).andExpect(jsonPath("$.items[0].rating").value(5));
        mvc.perform(get("/reviews/{id}/edit",review).with(user(""+other))).andExpect(status().isForbidden());
        mvc.perform(get("/reviews/{id}/edit",review).with(user(""+author))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/reviews/{id}",review).with(user(""+author)).with(csrf())).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from review_likes",Integer.class)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from review_comments",Integer.class)).isZero();
        mvc.perform(get("/api/v1/places/{id}/rating-summary",place)).andExpect(jsonPath("$.reviewCount").value(1)).andExpect(jsonPath("$.average").value(1.0));
    }
    @Test void validationAndTemplates() throws Exception {
        String payload="{\"placeId\":"+place+",\"rating\":0,\"body\":\"short\",\"visitDate\":\"2999-01-01\"}";
        mvc.perform(post("/api/v1/reviews").with(user(""+author)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());
        mvc.perform(get("/api/v1/places/{id}/reviews",place).param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places/{id}/reviews",place).param("sort","invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/99999999/reviews")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/places/99999999/rating-summary")).andExpect(status().isNotFound());
        mvc.perform(get("/reviews/new").param("placeId",""+place).with(user(""+author))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Write a review")));
        mvc.perform(post("/reviews/new").with(user(""+author)).with(csrf()).param("placeId",""+place)
                        .param("rating","0").param("body","short"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-describedby=\"rating-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-describedby=\"body-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">short</textarea>")));
        mvc.perform(post("/reviews/new").with(user(""+author)).with(csrf()).param("placeId",""+place).param("rating","5").param("body","A thoughtful review")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/users/{id}",author)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("A thoughtful review")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/places/{id}/rating-summary")));
    }
}
