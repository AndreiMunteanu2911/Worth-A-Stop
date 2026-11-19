package com.locationreview.location_review_platform.controller.api;



import com.locationreview.location_review_platform.service.*;

import com.locationreview.location_review_platform.service.ReviewService.*;

import com.locationreview.location_review_platform.dto.response.PageResponse;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.net.URI;

import java.security.Principal;

import java.time.LocalDate;

import java.util.Map;

import org.springframework.http.*;

import org.springframework.web.bind.annotation.*;



@RestController @RequestMapping("/api/v1")
@ApiResponses({@ApiResponse(responseCode="400",description="Invalid input"), @ApiResponse(responseCode="401",description="Authentication required for mutations"), @ApiResponse(responseCode="403",description="Ownership or CSRF check failed"), @ApiResponse(responseCode="404",description="Resource not found"), @ApiResponse(responseCode="409",description="Review already exists")})

public class ReviewApiController {

    private final ReviewService reviews;

    public ReviewApiController(ReviewService reviews) { this.reviews=reviews; }

    public record CreateRequest(Long placeId,Integer rating,String body,LocalDate visitDate) {}

    @PostMapping("/reviews") @ApiResponse(responseCode="201",description="Review created") @Operation(summary="Publish a review",description="One review per resolved place and author; CSRF token required.",security=@SecurityRequirement(name="sessionCookie"))

    public ResponseEntity<ReviewView> create(Principal principal,@RequestBody CreateRequest request) { if(request.placeId()==null) throw bad(); var result=reviews.create(id(principal),request.placeId(),new Input(request.rating(),request.body(),request.visitDate())); return ResponseEntity.created(URI.create("/api/v1/reviews/"+result.id())).body(result); }

    @GetMapping("/reviews/{id}") @Operation(summary="Read a review") public ReviewView read(@PathVariable long id) { return reviews.read(id); }

    @PatchMapping("/reviews/{id}") @Operation(summary="Edit your review",description="Supply at least one field; null visitDate clears it. CSRF token required.",security=@SecurityRequirement(name="sessionCookie"))

    public ReviewView update(Principal principal,@PathVariable long id,@RequestBody Map<String,Object> fields) {

        if(fields.isEmpty() || !java.util.Set.of("rating","body","visitDate").containsAll(fields.keySet())) throw bad();

        var old=reviews.read(id); Integer rating=old.rating(); String body=old.body(); LocalDate date=old.visitDate();

        try { if(fields.containsKey("rating")) { Object value=fields.get("rating"); if (!(value instanceof Integer)) throw bad(); rating=(Integer)value; } if(fields.containsKey("body")) { if (!(fields.get("body") instanceof String)) throw bad(); body=(String)fields.get("body"); } if(fields.containsKey("visitDate")) { Object value=fields.get("visitDate"); if(value!=null && !(value instanceof String)) throw bad(); date=value==null?null:LocalDate.parse((String)value); } } catch(java.time.DateTimeException ex) { throw bad(); }

        return reviews.update(id(principal),id,new Input(rating,body,date));

    }

    @DeleteMapping("/reviews/{id}") @ApiResponse(responseCode="204",description="Review deleted") @Operation(summary="Delete your review",description="Requires CSRF token.",security=@SecurityRequirement(name="sessionCookie")) public ResponseEntity<Void> delete(Principal principal,@PathVariable long id) { reviews.delete(id(principal),id); return ResponseEntity.noContent().build(); }

    @GetMapping("/places/{id}/reviews") @Operation(summary="Browse a place's reviews",description="Zero-based page; size 1–50; sort newest, oldest, rating_high or rating_low.") public PageResponse<ReviewView> place(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="newest") String sort) { return reviews.forPlace(id,page,size,sort); }

    @GetMapping("/users/{id}/reviews") @Operation(summary="Browse a user's reviews") public PageResponse<ReviewView> user(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return reviews.forUser(id,page,size); }

    @GetMapping("/places/{id}/rating-summary") @Operation(summary="Read community rating distribution") public RatingSummary summary(@PathVariable long id) { return reviews.summary(id); }

    private long id(Principal p) { return Long.parseLong(p.getName()); }

    private AccountException bad() { return new AccountException(HttpStatus.BAD_REQUEST,"invalid_request","Invalid review request.",Map.of()); }

}

