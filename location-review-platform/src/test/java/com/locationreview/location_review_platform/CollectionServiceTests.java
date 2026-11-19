package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.locationreview.location_review_platform.entity.Collection;
import com.locationreview.location_review_platform.entity.*;
import com.locationreview.location_review_platform.repository.*;
import com.locationreview.location_review_platform.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

class CollectionServiceTests {
    CollectionRepository collections = mock(CollectionRepository.class);
    CollectionPlaceRepository members = mock(CollectionPlaceRepository.class);
    PlaceRepository places = mock(PlaceRepository.class);
    UserRepository users = mock(UserRepository.class);
    CollectionService service = new CollectionService(collections, members, places, users);
    Collection collection;
    @BeforeEach void setup() {
        collection = mock(Collection.class);
        var owner = mock(User.class);
        when(owner.getId()).thenReturn(1L);
        when(collection.getOwner()).thenReturn(owner);
        when(collection.getId()).thenReturn(10L);
        when(collection.getVisibility()).thenReturn(Visibility.PUBLIC);
        when(collections.lockById(10)).thenReturn(Optional.of(collection));
        when(collections.findById(10L)).thenReturn(Optional.of(collection));
        when(members.findByCollectionIdOrderByPositionAsc(10)).thenReturn(new ArrayList<>());
    }
    @Test void privateCollectionNeverLeaksToNonowners() {
        when(collection.getVisibility()).thenReturn(Visibility.PRIVATE);
        for (Long viewer : Arrays.asList(null, 2L)) assertThatThrownBy(() -> service.read(10, viewer)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(10, 2)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(members);
    }
    @Test void publicMutationRequiresOwner() {
        assertThatThrownBy(() -> service.update(10, 2, new CollectionService.Input("Title", "", Visibility.PUBLIC))).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(collection, never()).setTitle(any());
    }
    @Test void validationRejectsInvalidMetadataAndUnknownPatchFields() {
        for (var input : List.of(new CollectionService.Input(" ", "", Visibility.PRIVATE), new CollectionService.Input("x".repeat(101), "", Visibility.PUBLIC), new CollectionService.Input("Title", "x".repeat(1001), Visibility.PUBLIC), new CollectionService.Input("Title", "", null)))
            assertThatThrownBy(() -> service.create(1, input)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.patch(10, 1, Map.of("ownerId", 2))).isInstanceOf(AccountException.class);
        verifyNoInteractions(users);
    }
    @Test void metadataEditDoesNotRepublishButVisibilityTransitionDoes() {
        Instant original = Instant.parse("2020-01-01T00:00:00Z");
        when(collection.getPublishedAt()).thenReturn(original);
        service.update(10, 1, new CollectionService.Input(" Renamed ", " Description ", Visibility.PUBLIC));
        verify(collection, never()).setPublishedAt(any());
        verify(collection).setTitle("Renamed");
        service.update(10, 1, new CollectionService.Input("Title", "", Visibility.PRIVATE));
        verify(collection).setPublishedAt(null);
        when(collection.getVisibility()).thenReturn(Visibility.PRIVATE);
        service.update(10, 1, new CollectionService.Input("Title", "", Visibility.PUBLIC));
        verify(collection).setPublishedAt(argThat(value -> value != null && value.isAfter(original)));
    }
    @Test void invalidOrStaleOrdersLeaveMembershipsUntouched() {
        assertThatThrownBy(() -> service.reorder(10, 1, Arrays.asList((Long)null))).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.reorder(10, 1, List.of(100L))).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(members, never()).flush();
    }
    @Test void missingTargetsAreNotFound() {
        assertThatThrownBy(() -> service.read(99, 1L)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.add(10, 1, 99)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("place_not_found"));
        assertThatThrownBy(() -> service.remove(10, 1, 99)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("membership_not_found"));
        verify(members, never()).saveAndFlush(any());
    }
    @Test void listScopeAndPaginationAreValidatedBeforeQuerying() {
        assertThatThrownBy(() -> service.list("mine", null, 0, 20)).isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.list("invalid", null, 0, 20)).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> service.list("public", null, -1, 20)).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> service.list("public", null, 0, 51)).isInstanceOf(AccountException.class);
        verify(collections, never()).findByVisibility(any(), any());
    }
}
