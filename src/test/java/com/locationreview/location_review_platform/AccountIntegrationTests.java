package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.assertThat;
import com.locationreview.location_review_platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTests {
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
    static { mysql.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
    @Autowired UserRepository users;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Test
    void accountApiAndBrowserSessionWorkflows() throws Exception {
        var csrf = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
        var guest = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me");
        mvc.perform(guest).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").with(csrf).contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").with(csrf).contentType("application/json")
                .content("{\"username\":\"  ALICE_1  \",\"email\":\"  ALICE@EXAMPLE.COM \",\"password\":\"a long password\",\"displayName\":\"Alice\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/api/v1/users/me"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.username").value("alice_1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").value("alice@example.com"))
            .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        var alice = users.findByUsername("alice_1").orElseThrow();
        assertThat(alice.getPasswordHash()).startsWith("$2").doesNotContain("a long password");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/{id}", alice.getId()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").doesNotExist())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/99999999"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/me").session(session).with(csrf).contentType("application/json").content("{\"username\":\"alice_new\",\"biography\":\"Hello\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.username").value("alice_new"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/settings/profile").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/{id}", alice.getId()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alice@example.com"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register").with(csrf).contentType("application/json")
                .content("{\"username\":\"otheruser\",\"email\":\"ALICE@example.com\",\"password\":\"a long password\",\"displayName\":\"Other\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout").session(session).with(csrf))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").with(csrf).param("username", " ALICE@EXAMPLE.COM ").param("password", "a long password"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/explore"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").with(csrf).param("username", "alice_new").param("password", "wrong"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?error"));
    }
    @Test
    void validationSecurityAndHtmlForms() throws Exception {
        var csrf = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/register").with(csrf)
            .param("username", "formuser").param("email", "form@example.test").param("displayName", "Form User").param("password", "long enough password");
        var created = mvc.perform(post).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) created.getRequest().getSession(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/settings/profile"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/me").session(session).contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/me").session(session).with(csrf).contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/settings/profile").session(session).with(csrf)
            .param("username", "bad!").param("email", "form@example.test").param("displayName", "Form User").param("biography", "Kept"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("username-error")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Kept")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/register").with(csrf)
            .param("username", "FORMUSER").param("email", "other@example.test").param("displayName", "Other").param("password", "long enough password"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("already in use")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout").session(session).with(csrf))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").with(csrf).param("username", " FORMUSER ").param("password", "long enough password"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/explore"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/explore"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("/api/v1/auth/register")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("/api/v1/users/me")));
    }
    @Test
    void sessionsCanOnlyChangeTheirOwnProfile() throws Exception {
        var csrf = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
        var first = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/register").with(csrf)
            .param("username", "owner_one").param("email", "one@example.test").param("displayName", "One").param("password", "long enough password"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection()).andReturn();
        var second = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/register").with(csrf)
            .param("username", "owner_two").param("email", "two@example.test").param("displayName", "Two").param("password", "long enough password"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection()).andReturn();
        var firstSession = (org.springframework.mock.web.MockHttpSession) first.getRequest().getSession(false);
        var secondSession = (org.springframework.mock.web.MockHttpSession) second.getRequest().getSession(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/me").session(secondSession).with(csrf)
            .contentType("application/json").content("{\"email\":\"ONE@EXAMPLE.TEST\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/users/me").session(secondSession).with(csrf)
            .contentType("application/json").content("{\"displayName\":\"Second changed\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me").session(firstSession))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.displayName").value("One"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me").session(secondSession))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.displayName").value("Second changed"));
    }
}
