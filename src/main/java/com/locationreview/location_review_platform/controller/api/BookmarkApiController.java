package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.service.BookmarkService;
import com.locationreview.location_review_platform.service.BookmarkService.BookmarkView;
import com.locationreview.location_review_platform.service.BookmarkService.SavedState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookmarks")
@Tag(name = "Bookmarks", description = "Private saved places for the signed-in user.")
@SecurityRequirement(name = "sessionCookie")
@ApiResponse(responseCode = "400", description = "Invalid place ID or pagination",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Authentication required",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
public class BookmarkApiController {
    private final BookmarkService bookmarks;

    public record BookmarkRequest(
            @NotNull @Positive @Digits(integer = 19, fraction = 0) @DecimalMax("9223372036854775807")
            @Schema(type = "integer", format = "int64", example = "42") BigDecimal placeId) {}

    public BookmarkApiController(BookmarkService bookmarks) {
        this.bookmarks = bookmarks;
    }

    @GetMapping
    @Operation(summary = "List your saved places",
            description = "Newest first by creation time and bookmark ID. Page is zero-based; size is 1–50.")
    @ApiResponse(responseCode = "200", description = "Page of saved places, including an empty page")
    public PageResponse<BookmarkView> list(Principal principal, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookmarks.list(Long.parseLong(principal.getName()), page, size);
    }

    @PostMapping
    @Operation(summary = "Save a resolved place", description = "Requires CSRF. Repeated saves retain the original timestamp.")
    @ApiResponse(responseCode = "200", description = "Place is saved")
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Place does not exist",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public SavedState save(Principal principal, @Valid @RequestBody BookmarkRequest input) {
        return bookmarks.save(Long.parseLong(principal.getName()), input.placeId().longValueExact());
    }

    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a saved place", description = "Requires CSRF. An existing place is successful even if it was already removed.")
    @ApiResponse(responseCode = "204", description = "Place is no longer saved", content = @Content)
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Place does not exist",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public void remove(Principal principal, @PathVariable long placeId) {
        bookmarks.remove(Long.parseLong(principal.getName()), placeId);
    }
}
