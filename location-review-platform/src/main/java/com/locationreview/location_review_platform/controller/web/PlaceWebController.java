package com.locationreview.location_review_platform.controller.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PlaceWebController {
    private final String browserKey;
    public PlaceWebController(@Value("${google.maps.browser-api-key:}") String browserKey) { this.browserKey=browserKey; }
    @GetMapping("/explore") String explore(Model model) { model.addAttribute("mapsBrowserKey",browserKey); return "explore"; }
    @GetMapping({"/places/{id}","/places/google/{id}"}) String place(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("placeApi",request.getRequestURI().contains("/google/")?"/api/v1/places/google/"+id:"/api/v1/places/"+id);
        model.addAttribute("mapsBrowserKey",browserKey); return "place";
    }
}
