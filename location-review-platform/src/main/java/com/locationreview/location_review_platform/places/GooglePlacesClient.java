package com.locationreview.location_review_platform.places;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** No persistent metadata cache, automatic retries, or background/bulk provider calls. */
@Component
public class GooglePlacesClient {
    private static final String FIELDS = "id,displayName,primaryType,types,location,formattedAddress,googleMapsUri,attributions";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String endpoint, key;
    public record Window(List<GooglePlace> items, boolean truncated) {}
    public GooglePlacesClient(ObjectMapper mapper,
            @Value("${google.places-url:https://places.googleapis.com/v1}") String endpoint,
            @Value("${google.maps.server-api-key:}") String key) {
        this.mapper = mapper; this.endpoint = endpoint.replaceAll("/+$", ""); this.key = key;
    }
    public Window search(String query, Double south, Double west, Double north, Double east,
                         Double lat, Double lon, String category) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("textQuery", query); body.put("pageSize", 20);
        if (category != null) { body.put("includedType", category); body.put("strictTypeFiltering", true); }
        if (south != null) body.put("locationRestriction", Map.of("rectangle", Map.of(
            "low", point(south, west), "high", point(north, east))));
        else if (lat != null) body.put("locationBias", Map.of("circle", Map.of("center", point(lat,lon), "radius", 5000)));
        return window(call("/places:searchText", body, "places." + FIELDS.replace(",", ",places.") + ",nextPageToken"));
    }
    public Window nearby(double lat, double lon, int radius, String category) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("maxResultCount",20); body.put("rankPreference","DISTANCE");
        body.put("locationRestriction", Map.of("circle", Map.of("center",point(lat,lon),"radius",radius)));
        if (category != null) body.put("includedTypes", List.of(category));
        Window result = window(call("/places:searchNearby", body, "places." + FIELDS.replace(",", ",places.")));
        return new Window(result.items(), result.items().size() == 20);
    }
    public GooglePlace details(String id) {
        if (!GooglePlace.validId(id)) throw new IllegalArgumentException("Invalid Google place ID");
        GooglePlace place = parse(call("/places/" + id, null, FIELDS + ",websiteUri,nationalPhoneNumber,regularOpeningHours.weekdayDescriptions"));
        if (!id.equals(place.placeId())) throw new PlaceProviderException(503,"Invalid place provider response.");
        return place;
    }
    private static Map<String,Double> point(double lat,double lon) { return Map.of("latitude",lat,"longitude",lon); }
    private JsonNode call(String path, Map<String,Object> body, String fields) {
        if (key.isBlank()) throw new PlaceProviderException(503,"Place discovery is not configured.");
        var builder = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(Duration.ofSeconds(15))
            .header("X-Goog-Api-Key",key).header("X-Goog-FieldMask",fields).header("Accept","application/json");
        if (body == null) builder.GET();
        else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        try {
            var response = http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new PlaceProviderException(429,"Place discovery quota has been reached. Please try later.");
            if (response.statusCode() == 404) throw new PlaceProviderException(404,"Place not found.");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new PlaceProviderException(503,"Place provider is unavailable. Check API configuration and billing.");
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isObject() || root.has("error")) throw new PlaceProviderException(503,"Invalid place provider response.");
            return root;
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new PlaceProviderException(503,"Place request interrupted."); }
        catch (java.io.IOException | tools.jackson.core.JacksonException ex) { throw new PlaceProviderException(503,"Place provider is unavailable."); }
    }
    private Window window(JsonNode root) {
        if (!root.has("places")) return new Window(List.of(),false);
        if (!root.path("places").isArray()) throw new PlaceProviderException(503,"Invalid place provider response.");
        List<GooglePlace> items = new ArrayList<>();
        for (JsonNode node : root.path("places")) { if (items.size() == 20) break; items.add(parse(node)); }
        return new Window(List.copyOf(items),root.has("nextPageToken") || items.size() == 20);
    }
    private GooglePlace parse(JsonNode node) {
        try {
            List<String> types = new ArrayList<>();
            for (JsonNode type : node.path("types")) types.add(type.asString());
            List<GooglePlace.Attribution> attributions = new ArrayList<>();
            List<String> hours = new ArrayList<>();
            for (JsonNode day : node.path("regularOpeningHours").path("weekdayDescriptions")) hours.add(day.asString());
            for (JsonNode attribution : node.path("attributions")) attributions.add(new GooglePlace.Attribution(
                attribution.path("provider").asString(""),attribution.path("providerUri").asString("")));
            return new GooglePlace(node.path("id").asString(""),node.path("displayName").path("text").asString(""),
                node.path("primaryType").asString("place"),types,
                new java.math.BigDecimal(node.path("location").path("latitude").asString()),
                new java.math.BigDecimal(node.path("location").path("longitude").asString()),
                node.path("formattedAddress").asString(""),node.path("googleMapsUri").asString(""),attributions,
                node.path("websiteUri").asString(""),node.path("nationalPhoneNumber").asString(""),hours);
        } catch (RuntimeException ex) { throw new PlaceProviderException(503,"Invalid place provider response."); }
    }
}
