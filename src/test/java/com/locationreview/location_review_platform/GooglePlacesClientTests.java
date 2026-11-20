package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.*;
import com.locationreview.location_review_platform.places.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

class GooglePlacesClientTests {
    HttpServer server;
    GooglePlacesClient client;
    AtomicInteger requests;
    volatile int status;
    volatile String body, path, method, fields, key, requestBody;
    static final String PLACE="{\"id\":\"ChIJ_library\",\"displayName\":{\"text\":\"Library\"},\"primaryType\":\"museum\",\"types\":[\"museum\"],\"location\":{\"latitude\":44.4,\"longitude\":26.1},\"formattedAddress\":\"City\",\"attributions\":[{\"provider\":\"Provider\",\"providerUri\":\"https://example.test\"}]}";
    @BeforeEach void setup() throws Exception {
        requests=new AtomicInteger();status=200;body="{}";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{requests.incrementAndGet();path=exchange.getRequestURI().toString();method=exchange.getRequestMethod();fields=exchange.getRequestHeaders().getFirst("X-Goog-FieldMask");key=exchange.getRequestHeaders().getFirst("X-Goog-Api-Key");requestBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.start();client=new GooglePlacesClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"fake-test-key");
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void textSearchUsesHeadersAndBoundedFields() {
        body="{\"places\":["+PLACE+"],\"nextPageToken\":\"next\"}";
        var result=client.search("Library",44d,26d,44.1,26.1,null,null,"museum");
        assertThat(result.items()).singleElement().satisfies(p->{assertThat(p.placeId()).isEqualTo("ChIJ_library");assertThat(p.attributions()).hasSize(1);});
        assertThat(result.truncated()).isTrue();assertThat(path).isEqualTo("/places:searchText");assertThat(method).isEqualTo("POST");
        assertThat(key).isEqualTo("fake-test-key");assertThat(path).doesNotContain(key);
        assertThat(fields).contains("places.displayName","nextPageToken").doesNotContain("rating","reviews","photos");
        assertThat(requestBody).contains("locationRestriction","pageSize", "museum");
    }
    @Test void nearbyAndDetailsUseNewApiWithoutPersistentCache() {
        body="{\"places\":["+PLACE+"]}";client.nearby(44.4,26.1,1500,"museum");
        assertThat(path).isEqualTo("/places:searchNearby");assertThat(requestBody).contains("maxResultCount", "DISTANCE");
        body=PLACE;client.details("ChIJ_library");client.details("ChIJ_library");
        assertThat(path).isEqualTo("/places/ChIJ_library");assertThat(method).isEqualTo("GET");assertThat(requests).hasValue(3);
    }
    @Test void emptyResultsAndProviderErrorsAreControlled() {
        assertThat(client.search("Empty",null,null,null,null,null,null,null).items()).isEmpty();
        for(int code:new int[]{400,401,403,404,429,500,503}) {
            status=code;assertThatThrownBy(()->client.details("ChIJ_library")).isInstanceOfSatisfying(PlaceProviderException.class,ex->assertThat(ex.status()).isEqualTo(code==404?404:code==429?429:503));
        }
        status=200;body="not json";assertThatThrownBy(()->client.details("ChIJ_library")).isInstanceOf(PlaceProviderException.class);
        body="{\"places\":false}";assertThatThrownBy(()->client.nearby(44,26,100,null)).isInstanceOf(PlaceProviderException.class);
        body=PLACE.replace("44.4","91");assertThatThrownBy(()->client.details("ChIJ_library")).isInstanceOf(PlaceProviderException.class);
    }
    @Test void absentKeyAndUnsafeIdsNeverContactProvider() {
        var disabled=new GooglePlacesClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"");
        assertThatThrownBy(()->disabled.details("ChIJ_library")).isInstanceOf(PlaceProviderException.class);
        assertThatThrownBy(()->client.details("../secret")).isInstanceOf(IllegalArgumentException.class);assertThat(requests).hasValue(0);
    }
    @Test void detailsRetainOptionalFieldsOnlyInTheResponse() {
        body=PLACE.substring(0,PLACE.length()-1)+",\"websiteUri\":\"https://venue.example\",\"nationalPhoneNumber\":\"123\",\"regularOpeningHours\":{\"weekdayDescriptions\":[\"Monday: 09:00–17:00\"]}}";
        var place=client.details("ChIJ_library");
        assertThat(place.website()).isEqualTo("https://venue.example");assertThat(place.phone()).isEqualTo("123");assertThat(place.openingHours()).hasSize(1);
        assertThat(fields).contains("websiteUri","nationalPhoneNumber","regularOpeningHours.weekdayDescriptions");
    }
    @Test void stalledProviderHasABoundedTimeout() throws Exception {
        HttpServer stalled=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        stalled.createContext("/",exchange->{try{Thread.sleep(16000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}exchange.close();});
        stalled.start();
        try {
            var slow=new GooglePlacesClient(new ObjectMapper(),"http://127.0.0.1:"+stalled.getAddress().getPort(),"fake-test-key");
            assertThatThrownBy(()->slow.details("ChIJ_library")).isInstanceOf(PlaceProviderException.class);
        } finally {stalled.stop(0);}
    }
}
