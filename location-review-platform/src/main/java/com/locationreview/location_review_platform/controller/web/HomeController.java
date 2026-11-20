package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.CollectionService;
import com.locationreview.location_review_platform.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final ReviewService reviews;
    private final CollectionService collections;

    public HomeController(ReviewService reviews, CollectionService collections) {
        this.reviews = reviews;
        this.collections = collections;
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("recentReviews", reviews.recent());
        model.addAttribute("publicCollections", collections.list("public", null, 0, 3).items());
        return "home";
    }
    @GetMapping("/privacy")
    String privacy() { return "privacy"; }
    @GetMapping("/terms")
    String terms() { return "terms"; }

}
