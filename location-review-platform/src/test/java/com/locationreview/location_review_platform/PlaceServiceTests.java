package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.locationreview.location_review_platform.places.*;
import com.locationreview.location_review_platform.repository.*;
import com.locationreview.location_review_platform.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class PlaceServiceTests {
    GooglePlacesClient client=mock(GooglePlacesClient.class);
    PlaceRepository places=mock(PlaceRepository.class);
    ReviewRepository reviews=mock(ReviewRepository.class);
    PlaceService service=new PlaceService(client,places,reviews,mock(TransactionTemplate.class));
    static GooglePlace candidate() {return new GooglePlace("ChIJ_library","Library","museum",List.of("museum"),new BigDecimal("44"),new BigDecimal("26"),"City","https://maps.google.com/",List.of());}
    @Test void validatesBeforeProviderCalls() {
        assertThatThrownBy(()->service.search("x",null,null,null,null,null,null,null,null,"relevance",20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.search("park",0d,0d,1d,1d,null,null,null,null,"relevance",20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.nearby(44d,26d,5001,null,null,"distance",20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.nearby(Double.NaN,26d,1000,null,null,"distance",20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.nearby(44d,26d,1000,"arbitrary",null,"distance",20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.nearby(44d,26d,1000,null,null,"distance",21)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.resolve("../secret")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(client);
    }
    @Test void filtersTransientResultsWithoutPersistingMetadata() {
        when(client.search("Library",null,null,null,null,null,null,"museum")).thenReturn(new GooglePlacesClient.Window(List.of(candidate()),true));
        var result=service.search("Library",null,null,null,null,null,null,"museum",null,"relevance",20);
        assertThat(result.items()).singleElement().satisfies(p->{assertThat(p.googlePlaceId()).isEqualTo("ChIJ_library");assertThat(p.id()).isNull();});
        assertThat(result.truncated()).isTrue();
        assertThat(service.search("Library",null,null,null,null,null,null,"museum",4d,"rating",20).items()).isEmpty();
        verify(places,never()).saveAndFlush(any());
    }
    @Test void absentAndUnavailablePlacesHaveDifferentBehavior() {
        assertThatThrownBy(()->service.byId(123)).isInstanceOf(ResponseStatusException.class);
        when(client.details("ChIJ_library")).thenThrow(new PlaceProviderException(503,"Unavailable"));
        assertThatThrownBy(()->service.byReference("ChIJ_library")).isInstanceOf(PlaceProviderException.class);
        var local=mock(com.locationreview.location_review_platform.entity.Place.class);when(local.getGooglePlaceId()).thenReturn("ChIJ_library");when(local.getId()).thenReturn(123L);
        when(places.findById(123L)).thenReturn(Optional.of(local));
        assertThat(service.byId(123).metadataUnavailable()).isTrue();
    }
    @Test void rejectsInvalidProviderCoordinates() {
        assertThatThrownBy(()->new GooglePlace("ChIJ_x","Name","park",List.of("park"),new BigDecimal("91"),BigDecimal.ZERO,null,null,List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
