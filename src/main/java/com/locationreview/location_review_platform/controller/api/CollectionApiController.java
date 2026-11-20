package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.entity.Visibility;
import com.locationreview.location_review_platform.service.CollectionService;
import com.locationreview.location_review_platform.service.CollectionService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name="Collections", description="Ordered guides of up to 100 resolved places; empty collections are valid.")
@ApiResponse(responseCode="400", description="Invalid input or pagination")
@ApiResponse(responseCode="401", description="Session required for mutations or scope=mine")
@ApiResponse(responseCode="403", description="CSRF rejected or public collection owned by another user")
@ApiResponse(responseCode="404", description="Missing target or private collection invisible to viewer")
@ApiResponse(responseCode="409", description="Duplicate place, full collection or stale order")
@RestController @RequestMapping("/api/v1")
public class CollectionApiController {
    private final CollectionService collections;
    public record CreateRequest(@NotBlank @Size(max=100) String title, @Size(max=1000) String description, @NotNull Visibility visibility) {
        public CreateRequest { title = title == null ? null : title.trim(); description = description == null ? null : description.trim(); }
    }
    public record PlaceRequest(@NotNull @Positive Long placeId) {}
    public record OrderRequest(@NotNull @Size(max=100) List<@NotNull @Positive Long> placeIds) {}
    public CollectionApiController(CollectionService collections) { this.collections=collections; }
    private Long id(Principal principal) { return principal==null?null:Long.valueOf(principal.getName()); }
    @GetMapping("/collections") @Operation(summary="Browse public or your own collections",description="scope is public or mine; zero-based page, size 1–50.")
    public PageResponse<Summary> list(Principal principal,@RequestParam(defaultValue="public") String scope,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return collections.list(scope,id(principal),page,size); }
    @GetMapping("/users/{id}/collections") @Operation(summary="Browse a user's public collections")
    public PageResponse<Summary> byUser(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return collections.byUser(id,page,size); }
    @PostMapping("/collections") @Operation(summary="Create a collection",security=@SecurityRequirement(name="sessionCookie")) @ApiResponse(responseCode="201",description="Created")
    public ResponseEntity<View> create(Principal principal,@Valid @RequestBody CreateRequest input) { var result=collections.create(id(principal),new Input(input.title(),input.description(),input.visibility())); return ResponseEntity.created(URI.create("/api/v1/collections/"+result.id())).body(result); }
    @GetMapping("/collections/{id}") @Operation(summary="Read a collection",description="Private collections are visible only to their owner.")
    public View read(@PathVariable long id,Principal principal) { return collections.read(id,id(principal)); }
    @PatchMapping("/collections/{id}") @Operation(summary="Edit collection metadata",description="Requires ownership and a CSRF token.",security=@SecurityRequirement(name="sessionCookie"))
    public View patch(@PathVariable long id,Principal principal,@RequestBody Map<String,Object> fields) { return collections.patch(id,id(principal),fields); }
    @DeleteMapping("/collections/{id}") @Operation(summary="Delete a collection",security=@SecurityRequirement(name="sessionCookie")) @ApiResponse(responseCode="204",description="Deleted")
    public ResponseEntity<Void> delete(@PathVariable long id,Principal principal) { collections.delete(id,id(principal)); return ResponseEntity.noContent().build(); }
    @PostMapping("/collections/{id}/places") @Operation(summary="Append a resolved place",security=@SecurityRequirement(name="sessionCookie"))
    public View add(@PathVariable long id,Principal principal,@Valid @RequestBody PlaceRequest input) { return collections.add(id,id(principal),input.placeId()); }
    @PutMapping("/collections/{id}/places/order") @Operation(summary="Reorder all places",description="Supply an exact permutation of current place IDs.",security=@SecurityRequirement(name="sessionCookie"))
    public View order(@PathVariable long id,Principal principal,@Valid @RequestBody OrderRequest input) { return collections.reorder(id,id(principal),input.placeIds()); }
    @DeleteMapping("/collections/{id}/places/{placeId}") @Operation(summary="Remove a place",security=@SecurityRequirement(name="sessionCookie")) @ApiResponse(responseCode="204",description="Removed")
    public ResponseEntity<Void> remove(@PathVariable long id,@PathVariable long placeId,Principal principal) { collections.remove(id,id(principal),placeId); return ResponseEntity.noContent().build(); }
}
