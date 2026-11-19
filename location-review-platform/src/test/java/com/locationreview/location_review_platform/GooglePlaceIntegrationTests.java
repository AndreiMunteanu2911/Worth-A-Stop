package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.locationreview.location_review_platform.places.*;
import com.locationreview.location_review_platform.repository.*;
import com.locationreview.location_review_platform.service.PlaceService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"google.maps.server-api-key=server-sentinel","google.maps.browser-api-key=browser-sentinel"})
@AutoConfigureMockMvc
class GooglePlaceIntegrationTests {
    static final MySQLContainer mysql=new MySQLContainer("mysql:8.4");
    static {mysql.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);}
    @MockitoBean GooglePlacesClient provider;
    @Autowired MockMvc mvc;
    @Autowired PlaceService service;
    @Autowired PlaceRepository places;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.junit.jupiter.api.BeforeEach void clearIsolatedTestData() {
        jdbc.update("delete from reviews");
        jdbc.update("delete from places");
        jdbc.update("delete from users");
    }
    private GooglePlace candidate(String id){return new GooglePlace(id,"Museum","museum",List.of("museum"),new BigDecimal("44.4"),new BigDecimal("26.1"),"City",null,List.of());}
    @Test void routesPersistOnlyIdentityAndPreserveContentDuringOutage() throws Exception {
        var remote=candidate("ChIJ_route");when(provider.details(remote.placeId())).thenReturn(remote);
        when(provider.search("Museum",null,null,null,null,null,null,null)).thenReturn(new GooglePlacesClient.Window(List.of(remote),false));
        when(provider.nearby(44.4,26.1,1500,null)).thenReturn(new GooglePlacesClient.Window(List.of(remote),false));
        long countBefore=places.count();
        mvc.perform(get("/api/v1/places/search").param("q","Museum")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].googlePlaceId").value(remote.placeId()));
        assertThat(places.count()).isEqualTo(countBefore);
        mvc.perform(get("/api/v1/places/nearby").param("lat","44.4").param("lon","26.1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/places/google/{id}",remote.placeId())).andExpect(status().isOk());
        String payload="{\"googlePlaceId\":\"ChIJ_route\"}";
        mvc.perform(post("/api/v1/places/resolve").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/places/resolve").with(user("1")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/places/resolve").with(user("1")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isOk());
        var stored=places.findByGooglePlaceId(remote.placeId()).orElseThrow();long id=stored.getId();
        assertThat(stored.getName()).isNull();assertThat(stored.getLatitude()).isNull();assertThat(stored.getAddress()).isNull();assertThat(stored.getMetadataFetchedAt()).isNull();
        assertThat(service.resolve(remote.placeId()).id()).isEqualTo(id);
        mvc.perform(get("/api/v1/places/{id}",id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/places/{id}/recommendations",id)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        when(provider.details(remote.placeId())).thenThrow(new PlaceProviderException(503,"Unavailable"));
        mvc.perform(get("/api/v1/places/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.metadataUnavailable").value(true));
        assertThat(service.resolve(remote.placeId()).metadataUnavailable()).isTrue();
        assertThat(places.findById(id)).isPresent();
    }
    @Test void validationMissingAndProviderErrors() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("q","x")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places/nearby").param("lat","bad").param("lon","26")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/places/99999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/places/99999999/recommendations")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/places/resolve").with(user("1")).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/places/resolve").with(user("1")).with(csrf()).contentType("application/json").content("{" )).andExpect(status().isBadRequest());
        when(provider.details("ChIJ_missing")).thenThrow(new PlaceProviderException(404,"Missing"));
        mvc.perform(get("/api/v1/places/google/ChIJ_missing")).andExpect(status().isNotFound());
        when(provider.details("ChIJ_error")).thenThrow(new PlaceProviderException(429,"Limit"));
        mvc.perform(get("/api/v1/places/google/ChIJ_error")).andExpect(status().isTooManyRequests());
    }
    @Test void concurrentIdentityAndDatabaseMetadataInvariant() throws Exception {
        when(provider.details("ChIJ_concurrent")).thenReturn(candidate("ChIJ_concurrent"));
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var calls=java.util.stream.IntStream.range(0,4).<java.util.concurrent.Callable<Long>>mapToObj(i->()->service.resolve("ChIJ_concurrent").id()).toList();
            var ids=executor.invokeAll(calls);var unique=new HashSet<Long>();for(var id:ids)unique.add(id.get());assertThat(unique).hasSize(1);
        }
        assertThatThrownBy(()->jdbc.update("update places set name='Google metadata' where google_place_id='ChIJ_concurrent'" )).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void localRatingsAndRecommendationsUseOurReviews() {
        when(provider.details("ChIJ_first")).thenReturn(candidate("ChIJ_first"));when(provider.details("ChIJ_second")).thenReturn(candidate("ChIJ_second"));
        long first=service.resolve("ChIJ_first").id(),second=service.resolve("ChIJ_second").id();
        jdbc.update("insert into users(username,email,password_hash,display_name,biography,created_at,updated_at) values ('google_rating','google_rating@example.test','hash','Reader','',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        Long author=jdbc.queryForObject("select id from users where username='google_rating'",Long.class);
        jdbc.update("insert into reviews(author_id,place_id,rating,body,created_at,updated_at) values (?,?,4,'A useful review',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",author,second);
        assertThat(service.recommendations(first)).extracting(PlaceService.PlaceView::id).contains(second);
        when(provider.search("Museums",null,null,null,null,null,null,null)).thenReturn(new GooglePlacesClient.Window(List.of(candidate("ChIJ_first"),candidate("ChIJ_second")),false));
        assertThat(service.search("Museums",null,null,null,null,null,null,null,4d,"rating",20).items()).singleElement().satisfies(p->assertThat(p.id()).isEqualTo(second));
    }
    @Test void pagesExposeBrowserKeyOnlyAndDescribeNewApi() throws Exception {
        for(String path:List.of("/explore","/places/google/ChIJ_page","/places/1"))mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("browser-sentinel"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("server-sentinel"))));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/places/google/{ref}")));
        mvc.perform(get("/privacy")).andExpect(status().isOk());
        mvc.perform(get("/terms")).andExpect(status().isOk());
    }
}
