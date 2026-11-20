package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.entity.Place;
import com.locationreview.location_review_platform.places.*;
import com.locationreview.location_review_platform.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaceService {
    private static final Set<String> CATEGORIES = Set.of("restaurant","cafe","museum","park","lodging","shopping_mall");
    private final GooglePlacesClient client;
    private final PlaceRepository places;
    private final ReviewRepository reviews;
    private final TransactionTemplate transactions;
    public record PlaceView(Long id, String googlePlaceId, String name, String category,
                            BigDecimal latitude, BigDecimal longitude, String address, String googleMapsUri,
                            List<GooglePlace.Attribution> attributions, boolean metadataUnavailable,
                            Double distanceMeters, Double averageRating, long reviewCount,
                            String website, String phone, List<String> openingHours) {}
    public record Result(List<PlaceView> items, int limit, boolean truncated) {}
    public PlaceService(GooglePlacesClient client, PlaceRepository places, ReviewRepository reviews, TransactionTemplate transactions) {
        this.client=client; this.places=places; this.reviews=reviews; this.transactions=transactions;
    }
    public Result search(String q, Double south, Double west, Double north, Double east, Double lat, Double lon,
                         String category, Double minRating, String sort, int limit) {
        if (q == null || q.trim().length()<2 || q.trim().length()>100 || limit<1 || limit>20) bad();
        validateCenter(lat,lon); validateFilter(category,minRating);
        if (sort == null || !Set.of("relevance","distance","rating").contains(sort) || sort.equals("distance") && lat == null) bad();
        int bounds=(south==null?0:1)+(west==null?0:1)+(north==null?0:1)+(east==null?0:1);
        if (bounds!=0 && (bounds!=4 || !coordinate(south,-90,90) || !coordinate(north,-90,90)
            || !coordinate(west,-180,180) || !coordinate(east,-180,180) || south>=north || west>=east
            || north-south>.25 || east-west>.25)) bad();
        return result(client.search(q.trim(),south,west,north,east,lat,lon,category),lat,lon,category,minRating,sort,limit);
    }
    public Result nearby(Double lat, Double lon, int radius, String category, Double minRating, String sort, int limit) {
        validateCenter(lat,lon); validateFilter(category,minRating);
        if (lat==null || radius<100 || radius>5000 || limit<1 || limit>20 || sort==null || !Set.of("distance","rating").contains(sort)) bad();
        return result(client.nearby(lat,lon,radius,category),lat,lon,category,minRating,sort,limit);
    }
    private Result result(GooglePlacesClient.Window window, Double lat, Double lon, String category, Double minRating, String sort, int limit) {
        List<PlaceView> views=new ArrayList<>();
        for (GooglePlace candidate:window.items()) {
            if (category!=null && !candidate.types().contains(category) && !category.equals(candidate.category())) continue;
            Place local=places.findByGooglePlaceId(candidate.placeId()).orElse(null);
            PlaceView view=view(candidate,local,lat,lon);
            if (minRating==null || view.averageRating()!=null && view.averageRating()>=minRating) views.add(view);
        }
        if (sort.equals("distance")) views.sort(Comparator.comparing(PlaceView::distanceMeters).thenComparing(PlaceView::googlePlaceId));
        if (sort.equals("rating")) views.sort(Comparator.comparing(PlaceView::averageRating,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PlaceView::googlePlaceId));
        return new Result(List.copyOf(views.subList(0,Math.min(limit,views.size()))),limit,window.truncated() || views.size()>limit);
    }
    public PlaceView byId(long id) {
        Place local=places.findById(id).orElseThrow(PlaceService::missing);
        if (local.getGooglePlaceId()==null) return legacy(local);
        try { return view(client.details(local.getGooglePlaceId()),local,null,null); }
        catch (PlaceProviderException ex) { return unavailable(local); }
    }
    public PlaceView byReference(String id) {
        validateId(id,true);
        Place local=places.findByGooglePlaceId(id).orElse(null);
        try { return view(client.details(id),local,null,null); }
        catch (PlaceProviderException ex) { if(local!=null) return unavailable(local); throw ex; }
    }
    public PlaceView resolve(String id) {
        validateId(id,false);
        // Provider access precedes the short database transaction. Only its ID is retained.
        GooglePlace remote;
        try { remote=client.details(id); }
        catch (PlaceProviderException ex) {
            Place existing=places.findByGooglePlaceId(id).orElse(null);
            if(existing!=null) return unavailable(existing);
            throw ex;
        }
        Place local;
        try { local=transactions.execute(status -> {
            Place found=places.findByGooglePlaceId(id).orElse(null);
            if(found!=null) return found;
            Place created=new Place(); created.setGooglePlaceId(id); created.setSource("GOOGLE");
            created.setCreatedAt(Instant.now()); created.setUpdatedAt(created.getCreatedAt());
            return places.saveAndFlush(created);
        }); } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            local=places.findByGooglePlaceId(id).orElseThrow(() -> ex);
        }
        return view(remote,local,null,null);
    }
    public List<PlaceView> recommendations(long id) {
        PlaceView origin=byId(id);
        if(origin.metadataUnavailable() || origin.googlePlaceId()==null) return List.of();
        List<PlaceView> result=new ArrayList<>();
        // Bound provider traffic; never crawl all persisted places to generate recommendations.
        for(Place candidate:places.reviewedCandidates(id,org.springframework.data.domain.PageRequest.of(0,5))) {
            PlaceView item=byId(candidate.getId());
            if(!item.metadataUnavailable() && Objects.equals(origin.category(),item.category())
                && distance(origin.latitude().doubleValue(),origin.longitude().doubleValue(),item.latitude().doubleValue(),item.longitude().doubleValue())<=10000) result.add(item);
        }
        return List.copyOf(result);
    }
    private PlaceView view(GooglePlace p, Place local, Double lat, Double lon) {
        var rating=local==null?null:reviews.ratingForPlace(local.getId());
        return new PlaceView(local==null?null:local.getId(),p.placeId(),p.name(),p.category(),p.latitude(),p.longitude(),p.address(),p.googleMapsUri(),p.attributions(),false,
            lat==null?null:distance(lat,lon,p.latitude().doubleValue(),p.longitude().doubleValue()),rating==null?null:rating.getAverage(),rating==null?0:rating.getCount(),p.website(),p.phone(),p.openingHours());
    }
    private PlaceView unavailable(Place p) {
        var rating=reviews.ratingForPlace(p.getId());
        return new PlaceView(p.getId(),p.getGooglePlaceId(),"Place details unavailable",null,null,null,null,null,List.of(),true,null,
            rating==null?null:rating.getAverage(),rating==null?0:rating.getCount(),null,null,List.of());
    }
    private PlaceView legacy(Place p) {
        var rating=reviews.ratingForPlace(p.getId());
        return new PlaceView(p.getId(),null,p.getName(),p.getCategory(),p.getLatitude(),p.getLongitude(),p.getAddress(),null,List.of(),false,null,
            rating==null?null:rating.getAverage(),rating==null?0:rating.getCount(),p.getWebsite(),p.getPhone(),p.getOpeningHours()==null?List.of():List.of(p.getOpeningHours()));
    }
    private static double distance(double a,double b,double c,double d) { double x=Math.pow(Math.sin(Math.toRadians(c-a)/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(Math.toRadians(d-b)/2),2);return 12742000*Math.asin(Math.min(1,Math.sqrt(x))); }
    private static void validateCenter(Double lat,Double lon) { if((lat==null)!=(lon==null) || lat!=null && (!coordinate(lat,-90,90)||!coordinate(lon,-180,180))) bad(); }
    private static boolean coordinate(double n,double min,double max) { return Double.isFinite(n)&&n>=min&&n<=max; }
    private static void validateFilter(String category,Double rating) { if(category!=null&&!CATEGORIES.contains(category)||rating!=null&&(!Double.isFinite(rating)||rating<1||rating>5)) bad(); }
    private static void validateId(String id,boolean read) { if(!GooglePlace.validId(id)) throw new ResponseStatusException(read?HttpStatus.NOT_FOUND:HttpStatus.BAD_REQUEST,"Invalid Google place ID"); }
    private static void bad() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid discovery parameters"); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Place not found"); }
}
