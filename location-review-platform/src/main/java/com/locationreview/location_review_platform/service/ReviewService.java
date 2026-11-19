package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.entity.*;
import com.locationreview.location_review_platform.repository.*;
import com.locationreview.location_review_platform.dto.response.PageResponse;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final PlaceRepository places;
    private final UserRepository users;
    public record Author(long id, String username, String displayName) {}
    public record PlaceRef(long id, String name, String googlePlaceId) {}
    public record ReviewView(long id, Author author, PlaceRef place, int rating, String body, LocalDate visitDate, Instant createdAt, Instant updatedAt) {}
    public record RatingSummary(long reviewCount, Double average, Map<Integer, Long> ratingCounts) {}
    public record Input(Integer rating, String body, LocalDate visitDate) {}
    public ReviewService(ReviewRepository reviews, PlaceRepository places, UserRepository users) { this.reviews=reviews; this.places=places; this.users=users; }
    private AccountException error(HttpStatus status, String code, String message) { return new AccountException(status, code, message, Map.of()); }
    private Review get(long id) { return reviews.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"review_not_found","Review not found.")); }
    private void placeExists(long id) { if (!places.existsById(id)) throw error(HttpStatus.NOT_FOUND,"place_not_found","Place not found."); }
    private void validate(Input input) {
        Map<String,String> errors=new LinkedHashMap<>();
        if (input.rating()==null || input.rating()<1 || input.rating()>5) errors.put("rating","Choose a rating from 1 to 5.");
        if (input.body()==null || input.body().trim().length()<10 || input.body().trim().length()>5000) errors.put("body","Write 10 to 5000 characters.");
        if (input.visitDate()!=null && input.visitDate().isAfter(LocalDate.now(ZoneId.of("Europe/Bucharest")))) errors.put("visitDate","Visit date cannot be in the future.");
        if (!errors.isEmpty()) throw new AccountException(HttpStatus.BAD_REQUEST,"invalid_fields","Check the review fields.",errors);
    }
    private ReviewView view(Review r) { var a=r.getAuthor(); var p=r.getPlace(); return new ReviewView(r.getId(),new Author(a.getId(),a.getUsername(),a.getDisplayName()),new PlaceRef(p.getId(),p.getName(),p.getGooglePlaceId()),r.getRating(),r.getBody(),r.getVisitDate(),r.getCreatedAt(),r.getUpdatedAt()); }
    private void owner(Review r,long userId) { if (!r.getAuthor().getId().equals(userId)) throw error(HttpStatus.FORBIDDEN,"forbidden","Only the author can change this review."); }
    @Transactional public ReviewView create(long userId,long placeId,Input input) {
        validate(input); var place=places.findById(placeId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"place_not_found","Place not found."));
        var user=users.findById(userId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"user_not_found","User not found."));
        if (reviews.existsByAuthorIdAndPlaceId(userId,placeId)) throw error(HttpStatus.CONFLICT,"duplicate_review","You have already reviewed this place.");
        Review r=new Review(); r.setAuthor(user); r.setPlace(place); r.setRating(input.rating().byteValue()); r.setBody(input.body().trim()); r.setVisitDate(input.visitDate()); r.setCreatedAt(Instant.now()); r.setUpdatedAt(r.getCreatedAt());
        try { return view(reviews.saveAndFlush(r)); } catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"duplicate_review","You have already reviewed this place."); }
    }
    @Transactional(readOnly=true) public ReviewView read(long id) { return view(get(id)); }
    @Transactional public ReviewView update(long userId,long id,Input input) { var r=get(id); owner(r,userId); validate(input); r.setRating(input.rating().byteValue()); r.setBody(input.body().trim()); r.setVisitDate(input.visitDate()); r.setUpdatedAt(Instant.now()); return view(r); }
    @Transactional public void delete(long userId,long id) { var r=get(id); owner(r,userId); reviews.delete(r); reviews.flush(); }
    private Pageable pageable(int page,int size,String sort) {
        if (page<0 || size<1 || size>50 || page>10000) throw error(HttpStatus.BAD_REQUEST,"invalid_page","Invalid page or size.");
        Sort order=switch(sort) { case "newest" -> Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")); case "oldest" -> Sort.by(Sort.Order.asc("createdAt"),Sort.Order.asc("id")); case "rating_high" -> Sort.by(Sort.Order.desc("rating"),Sort.Order.desc("id")); case "rating_low" -> Sort.by(Sort.Order.asc("rating"),Sort.Order.asc("id")); default -> throw error(HttpStatus.BAD_REQUEST,"invalid_sort","Invalid sort."); };
        return PageRequest.of(page,size,order);
    }
    @Transactional(readOnly=true) public PageResponse<ReviewView> forPlace(long id,int page,int size,String sort) { placeExists(id); return PageResponse.from(reviews.findByPlaceId(id,pageable(page,size,sort)).map(this::view)); }
    @Transactional(readOnly=true) public PageResponse<ReviewView> forUser(long id,int page,int size) { if (!users.existsById(id)) throw error(HttpStatus.NOT_FOUND,"user_not_found","User not found."); return PageResponse.from(reviews.findByAuthorId(id,pageable(page,size,"newest")).map(this::view)); }
    @Transactional(readOnly=true) public RatingSummary summary(long id) { placeExists(id); var aggregate=reviews.ratingForPlace(id); Map<Integer,Long> counts=new LinkedHashMap<>(); for(int i=1;i<=5;i++) counts.put(i,0L); for(var b:reviews.distribution(id)) counts.put((int)b.getRating(),b.getCount()); return new RatingSummary(aggregate.getCount(),aggregate.getAverage(),counts); }
}
