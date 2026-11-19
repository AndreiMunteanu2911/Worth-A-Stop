package com.locationreview.location_review_platform.controller.web;

import java.security.Principal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationModel {
    @ModelAttribute("viewerId")
    public String viewerId(Principal principal) { return principal == null ? null : principal.getName(); }
}
