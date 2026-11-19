package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.dto.account.AccountDtos.*;
import com.locationreview.location_review_platform.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AccountWebController {
    private final AccountService accounts;
    private final ReviewService reviews;
    private final CollectionService collections;
    private final PeopleService people;

    public AccountWebController(AccountService accounts, ReviewService reviews, CollectionService collections, PeopleService people) {
        this.accounts = accounts;
        this.reviews = reviews;
        this.collections = collections;
        this.people = people;
    }
    @GetMapping("/login") String login() { return "account/login"; }
    @GetMapping("/register") String register(Model model) { model.addAttribute("values", new RegisterRequest("", "", "", "")); return "account/register"; }
    @PostMapping("/register") String registerSubmit(@ModelAttribute RegisterRequest values, Model model, HttpServletRequest request, HttpServletResponse response) {
        try { var user = accounts.register(values); SessionLogin.start(user.id(), request, response); return "redirect:/users/" + user.id(); }
        catch (AccountException ex) { model.addAttribute("values", new RegisterRequest(values.username(), values.email(), "", values.displayName())); model.addAttribute("errors", ex.fieldErrors()); model.addAttribute("message", ex.getMessage()); response.setStatus(ex.status().value()); return "account/register"; }
    }
    @GetMapping("/settings/profile") String settings(Principal principal, Model model) {
        var user = accounts.self(Long.parseLong(principal.getName())); model.addAttribute("values", new ProfilePatchRequest(user.username(), user.email(), user.displayName(), user.biography())); return "account/settings";
    }
    @PostMapping("/settings/profile") String settingsSubmit(Principal principal, @ModelAttribute ProfilePatchRequest values, Model model, HttpServletResponse response) {
        try { accounts.update(Long.parseLong(principal.getName()), values); return "redirect:/settings/profile?saved"; }
        catch (AccountException ex) { model.addAttribute("values", values); model.addAttribute("errors", ex.fieldErrors()); model.addAttribute("message", ex.getMessage()); response.setStatus(ex.status().value()); return "account/settings"; }
    }
    @GetMapping("/users/{id}") String profile(@PathVariable long id, Principal principal, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="0") int collectionPage, Model model) {
        try {
            Long viewerId = principal == null ? null : Long.valueOf(principal.getName());
            boolean owner = viewerId != null && viewerId == id;
            model.addAttribute("profile", accounts.publicProfile(id));
            model.addAttribute("collections", collections.byUser(id, collectionPage, 20));
            model.addAttribute("reviews", reviews.forUser(id, page, 20, viewerId));
            model.addAttribute("owner", owner);
            model.addAttribute("following", viewerId != null && !owner && people.isFollowing(viewerId, id));
            return "account/profile";
        }
        catch (AccountException ex) { throw new ResponseStatusException(ex.status(), ex.getMessage()); }
    }
}
