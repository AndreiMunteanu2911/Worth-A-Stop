package com.locationreview.location_review_platform;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.places.GooglePlace.Attribution;
import com.locationreview.location_review_platform.places.PlaceProviderException;
import com.locationreview.location_review_platform.service.PlaceService.PlaceView;
import com.locationreview.location_review_platform.service.PlaceService.Result;
import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class FoundationWebTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.repository.UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.repository.FollowRepository follows;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.PlaceService places;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.ReviewService reviews;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.ReviewInteractionService reviewInteractions;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.CollectionService collections;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.locationreview.location_review_platform.service.BookmarkService bookmarks;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void pageFixtures() {
        when(collections.list("public", null, 0, 3)).thenReturn(new PageResponse<>(List.of(), 0, 3, 0, 0));
    }

    @Test
    void homeRendersDesignShellAndCsrfToken() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Worth a Stop</title>")))
                .andExpect(content().string(containsString("aria-label=\"Worth a Stop home\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Discover places worth")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"csrf-token\"")))
                .andExpect(content().string(containsString("href=\"/\" aria-current=\"page\"")))
                .andExpect(content().string(containsString("tabindex=\"-1\"")))
                .andExpect(content().string(containsString("action=\"/explore\"")));
    }

    @Test
    void googlePlaceOffersReviewAndCollectionActionsDirectly() throws Exception {
        mvc.perform(get("/places/google/{id}", "ChIJ_Fixture")
                        .with(SecurityMockMvcRequestPostProcessors.user("7")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"write-review\"")))
                .andExpect(content().string(containsString("id=\"add-to-collection\"")))
                .andExpect(content().string(not(containsString("Enable reviews and collections"))));
    }

    @Test
    void exploreRendersResultsAndAttributionWithoutTheMapOrJavaScript() throws Exception {
        var place = new PlaceView(null, "ChIJ_Fixture", "<b>Local cafe</b>", "cafe",
                new BigDecimal("44.43"), new BigDecimal("26.10"), "Main Street", null,
                List.of(new Attribution("Local contributor", "https://example.test/maps"),
                        new Attribution("Credit without link", "javascript:example()")),
                false, null, 4.0, 2, null, null, List.of());
        when(places.search("coffee", null, null, null, null, null, null, "cafe", 3.0, "rating", 20))
                .thenReturn(new Result(List.of(place), 20, true));

        mvc.perform(get("/explore").param("q", "coffee").param("category", "cafe")
                        .param("minRating", "3").param("sort", "rating"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/explore\" aria-current=\"page\"")))
                .andExpect(content().string(containsString("action=\"/explore\"")))
                .andExpect(content().string(containsString("value=\"coffee\"")))
                .andExpect(content().string(containsString("href=\"/places/google/ChIJ_Fixture\"")))
                .andExpect(content().string(containsString("&lt;b&gt;Local cafe&lt;/b&gt;")))
                .andExpect(content().string(containsString("Results are limited.")))
                .andExpect(content().string(containsString("href=\"https://example.test/maps\"")))
                .andExpect(content().string(containsString("Credit without link")))
                .andExpect(content().string(not(containsString("javascript:example()"))));
    }

    @Test
    void exploreKeepsItsSearchFormOnUpstreamAndValidationErrors() throws Exception {
        when(places.search("coffee", null, null, null, null, null, null, null, null, "relevance", 20))
                .thenThrow(new PlaceProviderException(503, "Place search is temporarily unavailable."));
        mvc.perform(get("/explore").param("q", "coffee"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("role=\"alert\">Place search is temporarily unavailable.")))
                .andExpect(content().string(containsString("value=\"coffee\"")));
        mvc.perform(get("/explore").param("q", "coffee").param("minRating", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose a valid minimum rating.")));
    }

    @Test
    void registrationAssociatesErrorsAndDiscardsTheSubmittedPassword() throws Exception {
        mvc.perform(post("/register").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", "a").param("email", "test@example.invalid")
                        .param("displayName", "Test").param("password", "too-short"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("aria-describedby=\"username-error\"")))
                .andExpect(content().string(containsString("id=\"username-error\"")))
                .andExpect(content().string(containsString("aria-describedby=\"password-help password-error\"")))
                .andExpect(content().string(not(containsString("too-short"))));
    }

    @Test
    void anonymousApiRequestGetsProblemJson() throws Exception {
        mvc.perform(post("/api/v1/private").with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("authentication_required")));
    }

    @Test
    void unsafeRequestRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/private"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("access_denied")));
    }

    @Test
    void openApiDeclaresAllFortyCustomOperations() throws Exception {
        var response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Worth a Stop API"))).andReturn().getResponse();
        var document = json.readTree(response.getContentAsByteArray());
        var expected = """
                POST /auth/register
                GET /bookmarks
                POST /bookmarks
                DELETE /bookmarks/{placeId}
                DELETE /comments/{id}
                GET /collections
                POST /collections
                GET /collections/{id}
                PATCH /collections/{id}
                DELETE /collections/{id}
                POST /collections/{id}/places
                PUT /collections/{id}/places/order
                DELETE /collections/{id}/places/{placeId}
                GET /feed
                GET /places/search
                GET /places/nearby
                POST /places/resolve
                GET /places/google/{ref}
                GET /places/{id}
                GET /places/{id}/recommendations
                GET /places/{id}/reviews
                GET /places/{id}/rating-summary
                POST /reviews
                GET /reviews/{id}
                PATCH /reviews/{id}
                DELETE /reviews/{id}
                GET /reviews/{id}/comments
                POST /reviews/{id}/comments
                POST /reviews/{id}/likes
                DELETE /reviews/{id}/likes
                GET /users
                GET /users/me
                PATCH /users/me
                GET /users/{id}
                GET /users/{id}/collections
                GET /users/{id}/reviews
                GET /users/{id}/followers
                GET /users/{id}/following
                POST /users/{id}/follow
                DELETE /users/{id}/follow
                """.lines().toList();
        var actual = new HashSet<String>();
        var methods = java.util.Set.of("get", "post", "put", "patch", "delete");
        document.path("paths").properties().forEach(path -> path.getValue().properties().stream()
                .filter(operation -> methods.contains(operation.getKey()))
                .forEach(operation -> {
                    assertThat(path.getKey()).startsWith("/api/v1/");
                    assertThat(operation.getValue().path("summary").asString()).isNotBlank();
                    actual.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey().substring("/api/v1".length()));
                }));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(document.at("/components/securitySchemes/sessionCookie/name").asString()).isEqualTo("JSESSIONID");
    }

    @Test
    void swaggerUiAndItsConfigurationArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs")));
    }
}
