package com.locationreview.location_review_platform.controller.web;

import com.locationreview.location_review_platform.service.*;
import com.locationreview.location_review_platform.service.ReviewService.*;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ReviewWebController {
    private final ReviewService reviews;
    public ReviewWebController(ReviewService reviews) { this.reviews=reviews; }
    @GetMapping("/reviews/new") String create(@RequestParam long placeId, Model model) { try { reviews.summary(placeId); model.addAttribute("placeId",placeId); model.addAttribute("rating",""); model.addAttribute("body",""); model.addAttribute("visitDate",""); return "review/form"; } catch(AccountException e) { throw new ResponseStatusException(e.status(),e.getMessage()); } }
    @PostMapping("/reviews/new") String createSubmit(Principal principal,@RequestParam long placeId,@RequestParam(required=false) String rating,@RequestParam(required=false) String body,@RequestParam(required=false) String visitDate,Model model,jakarta.servlet.http.HttpServletResponse response) { try { var r=reviews.create(id(principal),placeId,input(rating,body,visitDate)); return "redirect:/places/"+r.place().id(); } catch(AccountException e) { return failure(e,model,response,placeId,null,rating,body,visitDate); } }
    @GetMapping("/reviews/{id}/edit") String edit(@PathVariable long id,Principal principal,Model model) { try { var r=reviews.read(id, null); if(r.author().id()!=id(principal)) throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN); model.addAttribute("placeId",r.place().id()); model.addAttribute("reviewId",id); model.addAttribute("rating",r.rating()); model.addAttribute("body",r.body()); model.addAttribute("visitDate",r.visitDate()); return "review/form"; } catch(AccountException e) { throw new ResponseStatusException(e.status(),e.getMessage()); } }
    @PostMapping("/reviews/{id}/edit") String editSubmit(@PathVariable long id,Principal principal,@RequestParam(required=false) String rating,@RequestParam(required=false) String body,@RequestParam(required=false) String visitDate,Model model,jakarta.servlet.http.HttpServletResponse response) { try { var r=reviews.update(id(principal),id,input(rating,body,visitDate)); return "redirect:/places/"+r.place().id(); } catch(AccountException e) { if(e.status()!=org.springframework.http.HttpStatus.BAD_REQUEST) throw new ResponseStatusException(e.status(),e.getMessage()); var r=reviews.read(id, null); if(r.author().id()!=id(principal)) throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN); return failure(e,model,response,r.place().id(),id,rating,body,visitDate); } }
    @PostMapping("/reviews/{id}/delete") String delete(@PathVariable long id,Principal principal) { try { var r=reviews.read(id, null); reviews.delete(id(principal),id); return "redirect:/places/"+r.place().id(); } catch(AccountException e) { throw new ResponseStatusException(e.status(),e.getMessage()); } }
    private Input input(String rating,String body,String date) { try { return new Input(rating==null || rating.isBlank()?null:Integer.valueOf(rating),body,date==null || date.isBlank()?null:LocalDate.parse(date)); } catch(IllegalArgumentException e) { throw new AccountException(org.springframework.http.HttpStatus.BAD_REQUEST,"invalid_fields","Check the rating and date.",java.util.Map.of()); } }
    private long id(Principal p) { return Long.parseLong(p.getName()); }
    private String failure(AccountException e,Model m,jakarta.servlet.http.HttpServletResponse response,long placeId,Long reviewId,String rating,String body,String date) { if(e.status()!=org.springframework.http.HttpStatus.BAD_REQUEST && e.status()!=org.springframework.http.HttpStatus.CONFLICT) throw new ResponseStatusException(e.status(),e.getMessage()); response.setStatus(e.status().value()); m.addAttribute("placeId",placeId); m.addAttribute("reviewId",reviewId); m.addAttribute("rating",rating); m.addAttribute("body",body); m.addAttribute("visitDate",date); m.addAttribute("message",e.getMessage()); m.addAttribute("errors",e.fieldErrors()); return "review/form"; }
}
