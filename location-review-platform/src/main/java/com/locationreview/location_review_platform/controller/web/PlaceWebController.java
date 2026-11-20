package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.BookmarkService;
import com.locationreview.location_review_platform.service.PlaceService;
import com.locationreview.location_review_platform.places.PlaceProviderException;
import com.locationreview.location_review_platform.places.GooglePlace.Attribution;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PlaceWebController {
    private final String browserKey;
    private final BookmarkService bookmarks;
    private final PlaceService places;

    public PlaceWebController(@Value("${google.maps.browser-api-key:}") String browserKey,
            BookmarkService bookmarks, PlaceService places) {
        this.browserKey = browserKey;
        this.bookmarks = bookmarks;
        this.places = places;
    }

    @GetMapping("/explore")
    String explore(@RequestParam(required = false) String q, @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String minRating, @RequestParam(defaultValue = "relevance") String sort,
            Model model, HttpServletResponse response) {
        model.addAttribute("mapsBrowserKey", browserKey);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("category", category);
        model.addAttribute("minRating", minRating);
        model.addAttribute("sort", sort);
        if (q != null) {
            try {
                var results = places.search(q, null, null, null, null, null, null,
                        category.isEmpty() ? null : category, minRating.isEmpty() ? null : Double.valueOf(minRating), sort, 20);
                model.addAttribute("searchResults", results);
                model.addAttribute("attributions", results.items().stream()
                        .flatMap(place -> place.attributions().stream())
                        .filter(attribution -> !attribution.provider().isBlank())
                        .map(attribution -> new Attribution(attribution.provider(), attributionLink(attribution.providerUri())))
                        .distinct().toList());
            } catch (NumberFormatException ex) {
                response.setStatus(400);
                model.addAttribute("searchError", "Choose a valid minimum rating.");
            } catch (ResponseStatusException ex) {
                response.setStatus(ex.getStatusCode().value());
                model.addAttribute("searchError", ex.getReason());
            } catch (PlaceProviderException ex) {
                response.setStatus(ex.status());
                model.addAttribute("searchError", ex.getMessage());
            }
        }
        return "explore";
    }

    private static String attributionLink(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ex) {
            // The provider name remains visible when its URL is malformed.
        }
        return null;
    }
    @GetMapping("/places/{id}")
    String place(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("placeApi", "/api/v1/places/" + id);
        model.addAttribute("bookmarked", principal != null && bookmarks.isSaved(Long.parseLong(principal.getName()), id));
        model.addAttribute("mapsBrowserKey", browserKey);
        return "place";
    }

    @GetMapping("/places/google/{id}")
    String googlePlace(@PathVariable String id, Principal principal, Model model) {
        model.addAttribute("placeApi", "/api/v1/places/google/" + id);
        model.addAttribute("bookmarked", principal != null && bookmarks.isGooglePlaceSaved(Long.parseLong(principal.getName()), id));
        model.addAttribute("mapsBrowserKey", browserKey);
        return "place";
    }
}
