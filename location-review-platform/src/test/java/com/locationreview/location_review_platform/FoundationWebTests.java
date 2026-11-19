package com.locationreview.location_review_platform;

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

    @Autowired
    MockMvc mvc;

    @Test
    void homeRendersDesignShellAndCsrfToken() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Discover places worth")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"csrf-token\"")));
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
    void openApiIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Location Review Platform API")));
    }
}
