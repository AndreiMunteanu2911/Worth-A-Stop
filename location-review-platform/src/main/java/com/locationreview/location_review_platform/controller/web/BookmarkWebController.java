package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.BookmarkService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/saved")
public class BookmarkWebController {
    private final BookmarkService bookmarks;

    public BookmarkWebController(BookmarkService bookmarks) {
        this.bookmarks = bookmarks;
    }

    @GetMapping
    String list(Principal principal, @RequestParam(defaultValue = "0") int page, Model model,
            RedirectAttributes redirect) {
        var saved = bookmarks.list(Long.parseLong(principal.getName()), page, 20);
        if (page > 0 && page >= saved.totalPages()) {
            if (model.containsAttribute("notice")) {
                redirect.addFlashAttribute("notice", model.getAttribute("notice"));
            }
            return "redirect:/saved?page=" + Math.max(0, saved.totalPages() - 1);
        }
        model.addAttribute("saved", saved);
        return "saved";
    }

    @PostMapping("/{placeId}/remove")
    String remove(Principal principal, @PathVariable long placeId, @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirect) {
        bookmarks.remove(Long.parseLong(principal.getName()), placeId);
        redirect.addAttribute("page", Math.max(0, page));
        redirect.addFlashAttribute("notice", "Place removed from your saved list.");
        return "redirect:/saved";
    }

    @ExceptionHandler(AccountException.class)
    String error(AccountException error, Model model, HttpServletResponse response) {
        response.setStatus(error.status().value());
        model.addAttribute("error", error.getMessage());
        return "saved";
    }
}
