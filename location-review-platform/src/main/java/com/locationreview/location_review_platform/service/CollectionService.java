package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.entity.Collection;
import com.locationreview.location_review_platform.entity.CollectionPlace;
import com.locationreview.location_review_platform.entity.Visibility;
import com.locationreview.location_review_platform.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService {
    private final CollectionRepository collections;
    private final CollectionPlaceRepository members;
    private final PlaceRepository places;
    private final UserRepository users;
    public record Owner(long id, String username, String displayName) {}
    public record PlaceItem(long id, String name, String googlePlaceId, int position) {}
    public record Summary(long id, Owner owner, String title, String description, Visibility visibility, Instant publishedAt, Instant createdAt, Instant updatedAt) {}
    public record View(long id, Owner owner, String title, String description, Visibility visibility, Instant publishedAt, Instant createdAt, Instant updatedAt, List<PlaceItem> places) {}
    public record Input(String title, String description, Visibility visibility) {}
    public CollectionService(CollectionRepository collections, CollectionPlaceRepository members, PlaceRepository places, UserRepository users) { this.collections=collections; this.members=members; this.places=places; this.users=users; }
    private AccountException error(HttpStatus status, String code, String message) { return new AccountException(status,code,message,Map.of()); }
    private Collection get(long id) { return collections.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"collection_not_found","Collection not found.")); }
    private Collection lock(long id) { return collections.lockById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"collection_not_found","Collection not found.")); }
    private void visible(Collection c, Long viewer) { if(c.getVisibility()==Visibility.PRIVATE && !Objects.equals(c.getOwner().getId(),viewer)) throw error(HttpStatus.NOT_FOUND,"collection_not_found","Collection not found."); }
    private void owner(Collection c,long viewer) { if(!c.getOwner().getId().equals(viewer)) throw error(HttpStatus.FORBIDDEN,"forbidden","Only the owner can change this collection."); }
    private void validate(Input input) {
        Map<String,String> errors=new LinkedHashMap<>();
        if(input.title()==null || input.title().trim().isEmpty() || input.title().trim().length()>100) errors.put("title","Title must contain 1 to 100 characters.");
        if(input.description()!=null && input.description().trim().length()>1000) errors.put("description","Description must contain at most 1000 characters.");
        if(input.visibility()==null) errors.put("visibility","Choose public or private.");
        if(!errors.isEmpty()) throw new AccountException(HttpStatus.BAD_REQUEST,"invalid_fields","Check the collection fields.",errors);
    }
    private Summary summary(Collection c) { var u=c.getOwner(); return new Summary(c.getId(),new Owner(u.getId(),u.getUsername(),u.getDisplayName()),c.getTitle(),c.getDescription(),c.getVisibility(),c.getPublishedAt(),c.getCreatedAt(),c.getUpdatedAt()); }
    private View view(Collection c) { var s=summary(c); var items=members.findByCollectionIdOrderByPositionAsc(c.getId()).stream().map(m -> new PlaceItem(m.getPlace().getId(),m.getPlace().getName(),m.getPlace().getGooglePlaceId(),m.getPosition())).toList(); return new View(s.id(),s.owner(),s.title(),s.description(),s.visibility(),s.publishedAt(),s.createdAt(),s.updatedAt(),items); }
    private Pageable pageable(int page,int size,String property) { if(page<0 || page>10000 || size<1 || size>50) throw error(HttpStatus.BAD_REQUEST,"invalid_page","Invalid page or size."); return PageRequest.of(page,size,Sort.by(Sort.Order.desc(property),Sort.Order.desc("id"))); }
    @Transactional(readOnly=true) public PageResponse<Summary> list(String scope, Long viewer,int page,int size) {
        if("public".equals(scope)) return PageResponse.from(collections.findByVisibility(Visibility.PUBLIC,pageable(page,size,"publishedAt")).map(this::summary));
        if("mine".equals(scope)) { if(viewer==null) throw error(HttpStatus.UNAUTHORIZED,"authentication_required","Authentication is required."); return PageResponse.from(collections.findByOwnerId(viewer,pageable(page,size,"updatedAt")).map(this::summary)); }
        throw error(HttpStatus.BAD_REQUEST,"invalid_scope","Invalid collection scope.");
    }
    @Transactional(readOnly=true) public PageResponse<Summary> byUser(long id,int page,int size) { if(!users.existsById(id)) throw error(HttpStatus.NOT_FOUND,"user_not_found","User not found."); return PageResponse.from(collections.findByOwnerIdAndVisibility(id,Visibility.PUBLIC,pageable(page,size,"publishedAt")).map(this::summary)); }
    @Transactional(readOnly=true) public View read(long id,Long viewer) { var c=get(id); visible(c,viewer); return view(c); }
    @Transactional public View create(long ownerId,Input input) {
        validate(input); var user=users.findById(ownerId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"user_not_found","User not found."));
        var c=new Collection(); c.setOwner(user); c.setTitle(input.title().trim()); c.setDescription(input.description()==null?"":input.description().trim()); c.setVisibility(input.visibility()); Instant now=Instant.now(); c.setCreatedAt(now); c.setUpdatedAt(now); c.setPublishedAt(input.visibility()==Visibility.PUBLIC?now:null); return view(collections.saveAndFlush(c));
    }
    @Transactional public View update(long id,long viewer,Input input) {
        var c=lock(id); owner(c,viewer); validate(input); c.setTitle(input.title().trim()); c.setDescription(input.description()==null?"":input.description().trim());
        if(c.getVisibility()!=input.visibility()) c.setPublishedAt(input.visibility()==Visibility.PUBLIC?Instant.now():null);
        c.setVisibility(input.visibility()); c.setUpdatedAt(Instant.now()); return view(c);
    }
    @Transactional public void delete(long id,long viewer) { var c=lock(id); owner(c,viewer); collections.delete(c); collections.flush(); }
    @Transactional public View add(long id,long viewer,long placeId) {
        var c=lock(id); owner(c,viewer); var place=places.findById(placeId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"place_not_found","Place not found.")); var current=members.findByCollectionIdOrderByPositionAsc(id);
        if(current.size()>=100 || current.stream().anyMatch(m->m.getPlace().getId().equals(placeId))) throw error(HttpStatus.CONFLICT,"collection_conflict","Place is already present or the collection is full.");
        var m=new CollectionPlace(); m.setCollection(c); m.setPlace(place); m.setPosition(current.size()); m.setAddedAt(Instant.now()); members.saveAndFlush(m); c.setUpdatedAt(Instant.now()); return view(c);
    }
    @Transactional public View reorder(long id,long viewer,List<Long> ids) {
        var c=lock(id); owner(c,viewer); if(ids==null || ids.size()>100 || ids.stream().anyMatch(Objects::isNull)) throw error(HttpStatus.BAD_REQUEST,"invalid_order","Invalid place order.");
        var current=members.findByCollectionIdOrderByPositionAsc(id); var existing=current.stream().map(m->m.getPlace().getId()).toList();
        if(ids.size()!=existing.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(existing))) throw error(HttpStatus.CONFLICT,"stale_order","Place order does not match the current collection.");
        reorderMembers(current,ids); c.setUpdatedAt(Instant.now()); return view(c);
    }
    @Transactional public void remove(long id,long viewer,long placeId) {
        var c=lock(id); owner(c,viewer); var current=members.findByCollectionIdOrderByPositionAsc(id); var target=current.stream().filter(m->m.getPlace().getId().equals(placeId)).findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND,"membership_not_found","Place is not in this collection."));
        members.delete(target); members.flush(); current.remove(target); reorderMembers(current,current.stream().map(m->m.getPlace().getId()).toList()); c.setUpdatedAt(Instant.now());
    }
    private void reorderMembers(List<CollectionPlace> current,List<Long> ids) {
        // Temporary positive positions avoid MySQL's immediate unique-position checks.
        for(var m:current) m.setPosition(m.getPosition()+1000); members.flush();
        Map<Long,CollectionPlace> byPlace=new HashMap<>(); for(var m:current) byPlace.put(m.getPlace().getId(),m);
        for(int i=0;i<ids.size();i++) byPlace.get(ids.get(i)).setPosition(i); members.flush();
    }
}
