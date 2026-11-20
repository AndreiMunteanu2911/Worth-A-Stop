package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.AccountService;
import com.locationreview.location_review_platform.service.PeopleService;
import com.locationreview.location_review_platform.service.PeopleService.Connections;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PeopleWebController {
    private final PeopleService people;
    private final AccountService accounts;

    public PeopleWebController(PeopleService people, AccountService accounts) {
        this.people = people;
        this.accounts = accounts;
    }

    @GetMapping("/people")
    String search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            Model model, HttpServletResponse response) {
        model.addAttribute("pageTitle", "People");
        model.addAttribute("peoplePage", true);
        model.addAttribute("q", q == null ? "" : q.strip());
        if (q != null) {
            try {
                model.addAttribute("people", people.search(q, page, 20));
            } catch (AccountException ex) {
                response.setStatus(ex.status().value());
                model.addAttribute("searchError", ex.getMessage());
            }
        }
        return "account/people";
    }

    @GetMapping("/users/{id}/followers")
    String followers(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        return connections(id, page, model, Connections.FOLLOWERS);
    }

    @GetMapping("/users/{id}/following")
    String following(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        return connections(id, page, model, Connections.FOLLOWING);
    }

    private String connections(long id, int page, Model model, Connections direction) {
        try {
            model.addAttribute("people", people.connections(id, direction, page, 20));
            model.addAttribute("profile", accounts.publicProfile(id));
            model.addAttribute("peoplePage", false);
            boolean followers = direction == Connections.FOLLOWERS;
            model.addAttribute("pageTitle", followers ? "Followers" : "Following");
            model.addAttribute("directoryPath", "/users/" + id + (followers ? "/followers" : "/following"));
            model.addAttribute("emptyMessage", followers ? "No followers yet." : "This person hasn’t followed anyone yet.");
            return "account/people";
        } catch (AccountException ex) {
            throw new ResponseStatusException(ex.status(), ex.getMessage());
        }
    }

    @PostMapping("/users/{id}/follow")
    String updateFollow(@PathVariable long id, @RequestParam boolean following, Principal principal,
            RedirectAttributes redirect) {
        long viewerId = Long.parseLong(principal.getName());
        try {
            if (following) {
                people.follow(viewerId, id);
            } else {
                people.unfollow(viewerId, id);
            }
        } catch (AccountException ex) {
            throw new ResponseStatusException(ex.status(), ex.getMessage());
        }
        redirect.addFlashAttribute("followNotice", following ? "You’re now following this person." : "You unfollowed this person.");
        return "redirect:/users/" + id;
    }
}
