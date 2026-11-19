package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.*;
import com.locationreview.location_review_platform.entity.Visibility;
import java.security.Principal;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class CollectionWebController {
    private final CollectionService collections;
    public CollectionWebController(CollectionService collections) { this.collections = collections; }
    private Long viewer(Principal principal) { return principal == null ? null : Long.valueOf(principal.getName()); }

    @GetMapping("/collections")
    String index(Principal principal, @RequestParam(defaultValue="public") String scope,
            @RequestParam(defaultValue="0") int page, @RequestParam(required=false) Long placeId, Model model) {
        if ("mine".equals(scope) && principal == null) return "redirect:/login";
        model.addAttribute("collections", collections.list(scope, viewer(principal), page, 20));
        model.addAttribute("scope", scope); model.addAttribute("placeId", placeId);
        return "collection/index";
    }
    @GetMapping("/collections/{id}")
    String detail(@PathVariable long id, Principal principal, Model model) {
        var collection = collections.read(id, viewer(principal));
        model.addAttribute("collection", collection);
        model.addAttribute("owner", principal != null && collection.owner().id() == viewer(principal));
        return "collection/detail";
    }
    @GetMapping("/collections/new")
    String create(Model model) { model.addAttribute("values", new CollectionService.Input("", "", Visibility.PRIVATE)); return "collection/form"; }
    @PostMapping("/collections/new")
    String createSubmit(Principal principal, @RequestParam String title, @RequestParam(defaultValue="") String description,
            @RequestParam Visibility visibility, Model model, HttpServletResponse response) {
        var input = new CollectionService.Input(title, description, visibility);
        try { return "redirect:/collections/" + collections.create(viewer(principal), input).id() + "?created"; }
        catch (AccountException e) { return formFailure(e, input, null, model, response); }
    }
    @GetMapping("/collections/{id}/edit")
    String edit(@PathVariable long id, Principal principal, Model model) {
        var c = collections.read(id, viewer(principal));
        if (c.owner().id() != viewer(principal)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        model.addAttribute("collectionId", id); model.addAttribute("values", new CollectionService.Input(c.title(), c.description(), c.visibility()));
        return "collection/form";
    }
    @PostMapping("/collections/{id}/edit")
    String editSubmit(@PathVariable long id, Principal principal, @RequestParam String title,
            @RequestParam(defaultValue="") String description, @RequestParam Visibility visibility, Model model, HttpServletResponse response) {
        var input = new CollectionService.Input(title, description, visibility);
        try { collections.update(id, viewer(principal), input); return "redirect:/collections/" + id + "?updated"; }
        catch (AccountException e) { return formFailure(e, input, id, model, response); }
    }
    @PostMapping("/collections/{id}/delete")
    String delete(@PathVariable long id, Principal principal) { collections.delete(id, viewer(principal)); return "redirect:/collections?scope=mine&deleted"; }
    @PostMapping("/collections/{id}/places")
    String add(@PathVariable long id, Principal principal, @RequestParam long placeId, Model model, HttpServletResponse response) {
        try { collections.add(id, viewer(principal), placeId); return "redirect:/collections/" + id + "?updated"; }
        catch (AccountException e) { return detailFailure(e, id, principal, model, response); }
    }
    @PostMapping("/collections/{id}/places/{placeId}/remove")
    String remove(@PathVariable long id, @PathVariable long placeId, Principal principal, Model model, HttpServletResponse response) {
        try { collections.remove(id, viewer(principal), placeId); return "redirect:/collections/" + id + "?updated"; }
        catch (AccountException e) { return detailFailure(e, id, principal, model, response); }
    }
    @PostMapping("/collections/{id}/places/{placeId}/move")
    String move(@PathVariable long id, @PathVariable long placeId, Principal principal, @RequestParam String direction, Model model, HttpServletResponse response) {
        var c = collections.read(id, viewer(principal));
        if (c.owner().id() != viewer(principal)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var ids = new ArrayList<>(c.places().stream().map(CollectionService.PlaceItem::id).toList());
        int position = ids.indexOf(placeId), delta = "up".equals(direction) ? -1 : "down".equals(direction) ? 1 : 0;
        if (position < 0 || delta == 0 || position + delta < 0 || position + delta >= ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        java.util.Collections.swap(ids, position, position + delta);
        try { collections.reorder(id, viewer(principal), ids); return "redirect:/collections/" + id + "?updated"; }
        catch (AccountException e) { return detailFailure(e, id, principal, model, response); }
    }
    private String formFailure(AccountException e, CollectionService.Input input, Long id, Model model, HttpServletResponse response) {
        if (e.status() != HttpStatus.BAD_REQUEST) throw e;
        response.setStatus(e.status().value()); model.addAttribute("values", input); model.addAttribute("collectionId", id);
        model.addAttribute("message", e.getMessage()); model.addAttribute("errors", e.fieldErrors()); return "collection/form";
    }
    private String detailFailure(AccountException e, long id, Principal principal, Model model, HttpServletResponse response) {
        if (e.status() != HttpStatus.BAD_REQUEST && e.status() != HttpStatus.CONFLICT && !"membership_not_found".equals(e.code()) && !"place_not_found".equals(e.code())) throw e;
        response.setStatus(e.status().value()); model.addAttribute("message", e.getMessage()); return detail(id, principal, model);
    }
    @ExceptionHandler(AccountException.class)
    String error(AccountException e, Model model, HttpServletResponse response) {
        response.setStatus(e.status().value()); model.addAttribute("message", e.getMessage()); return "collection/error";
    }
}
