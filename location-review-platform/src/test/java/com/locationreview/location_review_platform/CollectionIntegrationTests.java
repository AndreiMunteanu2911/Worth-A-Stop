package com.locationreview.location_review_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import com.locationreview.location_review_platform.places.GooglePlacesClient;
import com.locationreview.location_review_platform.service.*;
import com.locationreview.location_review_platform.entity.Visibility;
import java.util.*;
import java.util.concurrent.*;
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
class CollectionIntegrationTests {
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
    static { mysql.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword);
    }
    @MockitoBean GooglePlacesClient provider;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CollectionService collections;
    long author, other, place, second;
    @BeforeEach void setup() {
        jdbc.update("delete from collections"); jdbc.update("delete from places"); jdbc.update("delete from users");
        jdbc.update("insert into users(username,email,password_hash,display_name,biography,created_at,updated_at) values ('author','author@example.test','hash','Author','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),('other','other@example.test','hash','Other','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        author = jdbc.queryForObject("select id from users where username='author'", Long.class);
        other = jdbc.queryForObject("select id from users where username='other'", Long.class);
        jdbc.update("insert into places(google_place_id,source,created_at,updated_at) values ('ChIJ_collection_a','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),('ChIJ_collection_b','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        place = jdbc.queryForObject("select id from places where google_place_id='ChIJ_collection_a'", Long.class);
        second = jdbc.queryForObject("select id from places where google_place_id='ChIJ_collection_b'", Long.class);
    }
    long create(Visibility visibility) { return collections.create(author, new CollectionService.Input("City walks", "A personal guide", visibility)).id(); }
    @Test void privacyPublicationAndPagination() throws Exception {
        long id = create(Visibility.PRIVATE);
        mvc.perform(get("/api/v1/collections/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/collections/{id}", id).with(user(""+other))).andExpect(status().isNotFound());
        mvc.perform(get("/collections/{id}/edit", id).with(user(""+other))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/collections/{id}", id).with(user(""+other)).with(csrf()).contentType("application/json").content("{\"title\":\"Secret\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/collections")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/users/{id}/collections", author).with(user(""+author))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/users/{id}", author).with(user(""+author))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("City walks"))));
        mvc.perform(get("/api/v1/collections").param("scope", "mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/collections").param("scope", "mine").with(user(""+author))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch("/api/v1/collections/{id}", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"visibility\":\"PUBLIC\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").isNotEmpty());
        var published = collections.read(id, null).publishedAt();
        collections.patch(id, author, Map.of("title", "An updated guide"));
        assertThat(collections.read(id, null).publishedAt()).isEqualTo(published);
        mvc.perform(get("/api/v1/users/{id}/collections", author)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/users/{id}", author)).andExpect(content().string(containsString("An updated guide")));
        mvc.perform(patch("/api/v1/collections/{id}", id).with(user(""+other)).with(csrf()).contentType("application/json").content("{\"title\":\"No\"}")).andExpect(status().isForbidden());
        collections.patch(id, author, Map.of("visibility", "PRIVATE"));
        assertThat(collections.read(id, author).publishedAt()).isNull();
        mvc.perform(get("/api/v1/collections")).andExpect(jsonPath("$.totalElements").value(0));
        collections.patch(id, author, Map.of("visibility", "PUBLIC"));
        assertThat(collections.read(id, null).publishedAt()).isAfter(published);
        long newer = create(Visibility.PUBLIC);
        mvc.perform(get("/api/v1/collections").param("size", "1")).andExpect(jsonPath("$.items[0].id").value(newer)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/collections").param("size", "1").param("page", "1")).andExpect(jsonPath("$.items[0].id").value(id));
    }
    @Test void apiLifecycleOrderingAndDeletion() throws Exception {
        mvc.perform(post("/api/v1/collections").with(user(""+author)).with(csrf()).contentType("application/json").content("{\"title\":\"Guide\",\"visibility\":\"PUBLIC\"}")).andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.places").isEmpty());
        long id = jdbc.queryForObject("select id from collections", Long.class);
        for (long p : List.of(place, second)) mvc.perform(post("/api/v1/collections/{id}/places", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeId\":"+p+"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/collections/{id}/places", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeId\":"+place+"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/collections/{id}/places/order", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeIds\":["+second+","+place+"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.places[0].id").value(second)).andExpect(jsonPath("$.places[1].position").value(1));
        mvc.perform(put("/api/v1/collections/{id}/places/order", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeIds\":["+place+"]}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/collections/{id}/places/{place}", id, second).with(user(""+author)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/collections/{id}", id)).andExpect(jsonPath("$.places[0].id").value(place)).andExpect(jsonPath("$.places[0].position").value(0));
        mvc.perform(get("/api/v1/collections")).andExpect(jsonPath("$.items[0].previewPlaces[0].id").value(place));
        mvc.perform(delete("/api/v1/collections/{id}/places/{place}", id, second).with(user(""+author)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/collections/{id}", id).with(user(""+other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/collections/{id}", id).with(user(""+author)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from collection_places", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from places", Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/v1/collections/{id}", id)).andExpect(status().isNotFound());
    }
    @Test void validationAndSecurity() throws Exception {
        String body = "{\"title\":\"Guide\",\"visibility\":\"PUBLIC\"}";
        mvc.perform(post("/api/v1/collections").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/collections").with(user(""+author)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/collections").with(user(""+author)).with(csrf()).contentType("application/json").content("{\"title\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/collections").param("scope", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/collections").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/collections").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/999999/collections")).andExpect(status().isNotFound());
        long id = create(Visibility.PUBLIC);
        for (String invalid : List.of("{}", "{\"title\":null}", "{\"visibility\":\"OTHER\"}", "{\"title\":12}", "{\"unknown\":true}"))
            mvc.perform(patch("/api/v1/collections/{id}", id).with(user(""+author)).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        for (String invalid : List.of("{\"placeIds\":null}", "{\"placeIds\":[null]}", "{\"placeIds\":[-1]}"))
            mvc.perform(put("/api/v1/collections/{id}/places/order", id).with(user(""+author)).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/collections/{id}/places", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeId\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/collections/{id}/places", id).with(user(""+author)).with(csrf()).contentType("application/json").content("{\"placeId\":999999}")).andExpect(status().isNotFound());
    }
    @Test void pagesAndForms() throws Exception {
        mvc.perform(get("/collections/new")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/collections/new").with(user(""+author))).andExpect(status().isOk());
        mvc.perform(post("/collections/new").with(user(""+author)).with(csrf()).param("title", " ").param("visibility", "PRIVATE"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Title must contain")))
                .andExpect(content().string(containsString("aria-describedby=\"title-error\"")))
                .andExpect(content().string(containsString("id=\"title-error\"")));
        mvc.perform(post("/collections/new").with(user(""+author)).with(csrf()).param("title", "My route").param("visibility", "PUBLIC")).andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("select id from collections", Long.class);
        mvc.perform(get("/collections").with(user(""+author)).param("scope", "mine").param("placeId", ""+place)).andExpect(status().isOk()).andExpect(content().string(containsString("Add this place")));
        mvc.perform(get("/collections/{id}/edit", id).with(user(""+author))).andExpect(status().isOk());
        mvc.perform(post("/collections/{id}/edit", id).with(user(""+author)).with(csrf()).param("title", "Edited route").param("visibility", "PUBLIC")).andExpect(status().is3xxRedirection());
        for (long p : List.of(place, second)) mvc.perform(post("/collections/{id}/places", id).with(user(""+author)).with(csrf()).param("placeId", ""+p)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/collections/{id}", id).with(user(""+author))).andExpect(status().isOk()).andExpect(content().string(containsString("Move up")));
        mvc.perform(post("/collections/{id}/places/{place}/move", id, second).with(user(""+author)).with(csrf()).param("direction", "up")).andExpect(status().is3xxRedirection());
        assertThat(collections.read(id, author).places().getFirst().id()).isEqualTo(second);
        mvc.perform(post("/collections/{id}/places/{place}/remove", id, second).with(user(""+author)).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/collections/{id}/places", id).with(user(""+author)).with(csrf()).param("placeId", ""+place)).andExpect(status().isConflict()).andExpect(content().string(containsString("already present")));
        mvc.perform(get("/collections/{id}", id)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Delete collection"))));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/collections/{id}/places/order'].put").exists()).andExpect(jsonPath("$.paths['/api/v1/users/{id}/collections'].get").exists());
        mvc.perform(post("/collections/{id}/delete", id).with(user(""+author)).with(csrf())).andExpect(status().is3xxRedirection());
    }
    @Test void everyMutationProtectsOwnershipPrivateVisibilityAndCsrf() throws Exception {
        for (var visibility : Visibility.values()) {
            long id = create(visibility);
            collections.add(id, author, place);
            for (int action=0; action<5; action++) {
                for (int actor=0; actor<3; actor++) {
                    var request = switch(action) {
                        case 0 -> patch("/api/v1/collections/{id}", id).content("{\"title\":\"Renamed\"}");
                        case 1 -> delete("/api/v1/collections/{id}", id);
                        case 2 -> post("/api/v1/collections/{id}/places", id).content("{\"placeId\":"+second+"}");
                        case 3 -> put("/api/v1/collections/{id}/places/order", id).content("{\"placeIds\":["+place+"]}");
                        default -> delete("/api/v1/collections/{id}/places/{place}", id, place);
                    };
                    request.contentType("application/json");
                    if (actor == 0) request.with(csrf());
                    if (actor == 1) request.with(user(""+other)).with(csrf());
                    if (actor == 2) request.with(user(""+author));
                    int expected = actor == 0 ? 401 : actor == 2 || visibility == Visibility.PUBLIC ? 403 : 404;
                    mvc.perform(request).andExpect(status().is(expected));
                }
            }
        }
    }
    @Test void concurrentAppendsAreSerializedAndPositionsStayDense() throws Exception {
        long id = create(Visibility.PRIVATE);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<Boolean>>();
            for (int i=0; i<2; i++) tasks.add(executor.submit(() -> { start.await(); try { collections.add(id, author, place); return true; } catch(AccountException e) { assertThat(e.status().value()).isEqualTo(409); return false; } }));
            start.countDown();
            int successes=0; for (var task : tasks) if(task.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(collections.read(id, author).places()).hasSize(1);
        assertThat(collections.read(id, author).places().getFirst().position()).isZero();
        collections.add(id, author, second);
        assertThatThrownBy(() -> jdbc.update("insert into collection_places(collection_id,place_id,position,added_at) values (?,?,2,UTC_TIMESTAMP(6))", id, second)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("insert into places(google_place_id,source,created_at,updated_at) values ('ChIJ_unique_position','GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        long third = jdbc.queryForObject("select id from places where google_place_id='ChIJ_unique_position'", Long.class);
        assertThatThrownBy(() -> jdbc.update("insert into collection_places(collection_id,place_id,position,added_at) values (?,?,0,UTC_TIMESTAMP(6))", id, third)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void maximumSizeAndLargeReorder() throws Exception {
        long id = create(Visibility.PRIVATE);
        for (int i=0; i<99; i++) {
            jdbc.update("insert into places(google_place_id,source,created_at,updated_at) values (?,'GOOGLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", "ChIJ_capacity_"+i);
            long p = jdbc.queryForObject("select id from places where google_place_id=?", Long.class, "ChIJ_capacity_"+i);
            collections.add(id, author, p);
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<Boolean>>();
            for (long p : List.of(place, second)) tasks.add(executor.submit(() -> { start.await(); try { collections.add(id, author, p); return true; } catch(AccountException e) { assertThat(e.status().value()).isEqualTo(409); return false; } }));
            start.countDown();
            int successes=0; for (var task : tasks) if(task.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(collections.read(id, author).places()).hasSize(100);
        var order = new ArrayList<>(collections.read(id, author).places().stream().map(CollectionService.PlaceItem::id).toList());
        java.util.Collections.reverse(order); collections.reorder(id, author, order);
        assertThat(collections.read(id, author).places().stream().map(CollectionService.PlaceItem::id)).containsExactlyElementsOf(order);
        collections.remove(id, author, order.getFirst());
        assertThat(collections.read(id, author).places().stream().map(CollectionService.PlaceItem::position)).containsExactlyElementsOf(java.util.stream.IntStream.range(0,99).boxed().toList());
    }
}
