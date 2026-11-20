package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.service.PlaceService;
import com.locationreview.location_review_platform.places.PlaceProviderException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/places")
@ApiResponses({@ApiResponse(responseCode="200",description="Place data or a bounded candidate window"),
    @ApiResponse(responseCode="400",description="Invalid input"),
    @ApiResponse(responseCode="404",description="Place not found"),
    @ApiResponse(responseCode="429",description="Provider request limit reached"),
    @ApiResponse(responseCode="503",description="Provider unavailable or not configured")})
public class PlaceApiController {
    private final PlaceService places;
    public PlaceApiController(PlaceService places) { this.places = places; }
    public record ResolveRequest(String googlePlaceId) {}
    @GetMapping("/search") @Operation(summary="Search named Google places", description="Explicit search; at most 20 candidates. Query length 2–100; complete bbox at most 0.25 degrees per axis. Distance sort needs lat/lon. Minimum rating filters this application's reviews only.")
    public PlaceService.Result search(@RequestParam String q, @RequestParam(required=false) Double south, @RequestParam(required=false) Double west, @RequestParam(required=false) Double north, @RequestParam(required=false) Double east, @RequestParam(required=false) Double lat, @RequestParam(required=false) Double lon, @RequestParam(required=false) String category, @RequestParam(required=false) Double minRating, @RequestParam(defaultValue="relevance") String sort, @RequestParam(defaultValue="20") int limit) { return places.search(q,south,west,north,east,lat,lon,category,minRating,sort,limit); }
    @GetMapping("/nearby") @Operation(summary="Discover nearby Google places",description="Radius 100–5000 meters; limit 1–20. Categories: restaurant, cafe, museum, park, lodging, shopping_mall. Filters apply to a bounded provider window.")
    public PlaceService.Result nearby(@RequestParam Double lat, @RequestParam Double lon, @RequestParam(defaultValue="1500") int radiusMeters, @RequestParam(required=false) String category, @RequestParam(required=false) Double minRating, @RequestParam(defaultValue="distance") String sort, @RequestParam(defaultValue="20") int limit) { return places.nearby(lat,lon,radiusMeters,category,minRating,sort,limit); }
    @GetMapping("/google/{ref}") @Operation(summary="Read a Google place without persisting it")
    public PlaceService.PlaceView remote(@PathVariable String ref) { return places.byReference(ref); }
    @GetMapping("/{id}") @Operation(summary="Read a saved identity with current place details")
    public PlaceService.PlaceView local(@PathVariable long id) { return places.byId(id); }
    @GetMapping("/{id}/recommendations") @Operation(summary="Read nearby reviewed places in the same category")
    public java.util.List<PlaceService.PlaceView> recommendations(@PathVariable long id) { return places.recommendations(id); }
    @PostMapping("/resolve") @Operation(summary="Verify and save a Google place", security=@SecurityRequirement(name="sessionCookie"), description="Requires a CSRF token. Stores only the verified Google ID; repeat/concurrent requests reuse the local identity.")
    @ApiResponses({@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="403",description="CSRF rejected")})
    public PlaceService.PlaceView resolve(@RequestBody ResolveRequest request) { return places.resolve(request.googlePlaceId()); }
    @ExceptionHandler(PlaceProviderException.class)
    ProblemDetail upstream(PlaceProviderException ex) {
        return problem(HttpStatus.valueOf(ex.status()), ex.getMessage(), "place_provider_error");
    }
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail invalid(ResponseStatusException ex) {
        var status = ex.getStatusCode();
        return problem(status, ex.getReason(), status.value() == 404 ? "place_not_found" : "invalid_discovery");
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    ProblemDetail malformed(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid discovery request.", "invalid_request");
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
