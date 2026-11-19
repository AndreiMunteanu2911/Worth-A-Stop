package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.ReviewInteractionService;
import com.locationreview.location_review_platform.service.ReviewService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewDiscussionController {
    private static final int PAGE_SIZE = 20;

    private final ReviewService reviews;
    private final ReviewInteractionService interactions;

    public ReviewDiscussionController(ReviewService reviews, ReviewInteractionService interactions) {
        this.reviews = reviews;
        this.interactions = interactions;
    }

    @GetMapping("/reviews/{id}")
    String discussion(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        try {
            var comments = interactions.comments(id, page, PAGE_SIZE);
            Long viewer = principal == null ? null : Long.valueOf(principal.getName());
            model.addAttribute("review", reviews.read(id, viewer));
            model.addAttribute("comments", comments);
            return "review/discussion";
        } catch (AccountException ex) {
            throw new ResponseStatusException(ex.status(), ex.getMessage());
        }
    }

    @PostMapping("/reviews/{id}/like")
    String like(@PathVariable long id, @RequestParam boolean liked, @RequestParam(defaultValue = "0") int page,
            Principal principal, RedirectAttributes redirect) {
        try {
            long userId = Long.parseLong(principal.getName());
            if (liked) {
                interactions.like(userId, id);
            } else {
                interactions.unlike(userId, id);
            }
        } catch (AccountException ex) {
            throw new ResponseStatusException(ex.status(), ex.getMessage());
        }
        redirect.addFlashAttribute("likeNotice", liked ? "Review liked." : "Like removed.");
        return "redirect:/reviews/" + id + "?page=" + Math.max(0, page) + "#review-likes";
    }

    @PostMapping("/reviews/{id}/comments")
    String addComment(@PathVariable long id, @RequestParam(required = false) String body,
            @RequestParam(defaultValue = "0") int page, Principal principal, Model model,
            HttpServletResponse response, RedirectAttributes redirect) {
        try {
            interactions.addComment(Long.parseLong(principal.getName()), id, body);
        } catch (AccountException ex) {
            if (ex.status() != HttpStatus.BAD_REQUEST || !ex.fieldErrors().containsKey("body")) {
                throw new ResponseStatusException(ex.status(), ex.getMessage());
            }
            response.setStatus(ex.status().value());
            model.addAttribute("commentBody", body);
            model.addAttribute("commentError", ex.fieldErrors().get("body"));
            return discussion(id, page, principal, model);
        }
        redirect.addFlashAttribute("commentNotice", "Comment posted.");
        return "redirect:/reviews/" + id + "?page=" + lastPage(id) + "#comments";
    }

    @PostMapping("/comments/{id}/delete")
    String deleteComment(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            Principal principal, RedirectAttributes redirect) {
        long reviewId;
        try {
            reviewId = interactions.deleteComment(Long.parseLong(principal.getName()), id);
        } catch (AccountException ex) {
            throw new ResponseStatusException(ex.status(), ex.getMessage());
        }
        redirect.addFlashAttribute("commentNotice", "Comment deleted.");
        return "redirect:/reviews/" + reviewId + "?page=" + Math.clamp(page, 0, lastPage(reviewId)) + "#comments";
    }

    private long lastPage(long reviewId) {
        return Math.max(0, interactions.commentCount(reviewId) - 1) / PAGE_SIZE;
    }
}
