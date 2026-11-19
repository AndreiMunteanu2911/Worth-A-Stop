package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.BookmarkService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PlaceWebController {
    private final String browserKey;
    private final BookmarkService bookmarks;
    public PlaceWebController(@Value("${google.maps.browser-api-key:}") String browserKey, BookmarkService bookmarks) {
        this.browserKey = browserKey;
        this.bookmarks = bookmarks;
    }
    @GetMapping("/explore") String explore(Model model) { model.addAttribute("mapsBrowserKey",browserKey); return "explore"; }
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
