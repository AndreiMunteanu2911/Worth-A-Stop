package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.FeedService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FeedWebController {
    private final FeedService feed;

    public FeedWebController(FeedService feed) {
        this.feed = feed;
    }

    @GetMapping("/feed")
    String read(Principal principal, @RequestParam(defaultValue = "0") int page, Model model,
            HttpServletResponse response) {
        model.addAttribute("feedPage", true);
        try {
            model.addAttribute("feed", feed.read(Long.parseLong(principal.getName()), page, 20));
        } catch (AccountException ex) {
            response.setStatus(ex.status().value());
            model.addAttribute("feedError", ex.getMessage());
        }
        return "feed";
    }
}
