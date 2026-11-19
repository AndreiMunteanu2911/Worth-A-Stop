package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.service.AccountException;
import com.locationreview.location_review_platform.service.ReviewInteractionService;
import com.locationreview.location_review_platform.service.ReviewInteractionService.CommentView;
import com.locationreview.location_review_platform.service.ReviewInteractionService.LikeState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Review interactions", description = "Review likes and public comments.")
@ApiResponse(responseCode = "400", description = "Invalid ID, request body or pagination",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Review or comment not found",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
public class ReviewInteractionApiController {
    public record CommentCreateRequest(
            @NotNull(message = "Enter 1–1000 characters after trimming.")
            @Schema(implementation = String.class, description = "1–1000 characters after trimming",
                    example = "Thanks for the tip about the opening hours.", requiredMode = Schema.RequiredMode.REQUIRED)
            JsonNode body) {}

    private final ReviewInteractionService interactions;

    public ReviewInteractionApiController(ReviewInteractionService interactions) {
        this.interactions = interactions;
    }

    @PostMapping("/reviews/{id}/likes")
    @Operation(summary = "Like a review", description = "Requires CSRF. Repeating a like preserves its original timestamp.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "200", description = "Review is liked, with current like count")
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public LikeState like(@PathVariable long id, Principal principal) {
        return interactions.like(Long.parseLong(principal.getName()), id);
    }

    @DeleteMapping("/reviews/{id}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unlike a review", description = "Requires CSRF. An already absent like on an existing review is a successful no-op.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "204", description = "Review is no longer liked", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public void unlike(@PathVariable long id, Principal principal) {
        interactions.unlike(Long.parseLong(principal.getName()), id);
    }

    @GetMapping("/reviews/{id}/comments")
    @Operation(summary = "List comments on a review",
            description = "Oldest first by creation time and comment ID. Page is zero-based; size is 1–50.")
    @ApiResponse(responseCode = "200", description = "Page of public comments")
    public PageResponse<CommentView> comments(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return interactions.comments(id, page, size);
    }

    @PostMapping("/reviews/{id}/comments")
    @Operation(summary = "Post a comment on a review", description = "Requires CSRF. Stores the trimmed comment as plain text.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "201", description = "Comment created with its resource URI in the Location header")
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<CommentView> addComment(@PathVariable long id, Principal principal,
            @Valid @RequestBody CommentCreateRequest request) {
        if (!request.body().isNull() && !request.body().isString()) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields", "Check the comment text.",
                    Map.of("body", "Use a JSON string."));
        }
        CommentView comment = interactions.addComment(Long.parseLong(principal.getName()), id,
                request.body().asString(null));
        return ResponseEntity.created(URI.create("/api/v1/comments/" + comment.id())).body(comment);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your comment", description = "Requires CSRF and comment authorship.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "204", description = "Comment deleted", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Comment belongs to another user or CSRF was rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public void deleteComment(@PathVariable long id, Principal principal) {
        interactions.deleteComment(Long.parseLong(principal.getName()), id);
    }
}
