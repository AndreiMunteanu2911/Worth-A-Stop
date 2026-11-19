package com.locationreview.location_review_platform.places;

import java.math.BigDecimal;
import java.util.List;

public record GooglePlace(String placeId, String name, String category, List<String> types,
                          BigDecimal latitude, BigDecimal longitude, String address,
                          String googleMapsUri, List<Attribution> attributions,
                          String website, String phone, List<String> openingHours) {
    public record Attribution(String provider, String providerUri) {}
    public GooglePlace(String placeId, String name, String category, List<String> types,
                       BigDecimal latitude, BigDecimal longitude, String address, String googleMapsUri,
                       List<Attribution> attributions) {
        this(placeId,name,category,types,latitude,longitude,address,googleMapsUri,attributions,null,null,List.of());
    }
    public GooglePlace {
        if (!validId(placeId) || name == null || name.isBlank() || latitude == null || longitude == null
                || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)
            throw new IllegalArgumentException("Invalid place metadata");
        types = List.copyOf(types);
        attributions = List.copyOf(attributions);
        openingHours = List.copyOf(openingHours);
    }
    public static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,255}"); }
}
