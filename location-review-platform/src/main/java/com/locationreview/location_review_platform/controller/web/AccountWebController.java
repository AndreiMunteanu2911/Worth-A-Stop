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
    public AccountWebController(AccountService accounts) { this.accounts = accounts; }
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
    @GetMapping("/users/{id}") String profile(@PathVariable long id, Principal principal, Model model) {
        try { model.addAttribute("profile", accounts.publicProfile(id)); model.addAttribute("owner", principal != null && principal.getName().equals(Long.toString(id))); return "account/profile"; }
        catch (AccountException ex) { throw new ResponseStatusException(ex.status(), ex.getMessage()); }
    }
}
