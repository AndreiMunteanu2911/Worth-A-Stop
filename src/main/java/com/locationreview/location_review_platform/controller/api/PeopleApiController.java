package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.account.AccountDtos.UserPublic;
import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.service.PeopleService;
import com.locationreview.location_review_platform.service.PeopleService.FollowState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import static com.locationreview.location_review_platform.service.PeopleService.Connections.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "People", description = "Public user discovery and follow relationships.")
@ApiResponse(responseCode = "400", description = "Invalid query, user ID or pagination",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
public class PeopleApiController {
    private final PeopleService people;

    public PeopleApiController(PeopleService people) {
        this.people = people;
    }

    @GetMapping
    @Operation(summary = "Search public profiles",
            description = "Matches username or display name, ordered by username and ID. Page is zero-based; size is 1–50.")
    @ApiResponse(responseCode = "200", description = "Page of matching profiles")
    public PageResponse<UserPublic> search(
            @Parameter(description = "Literal search term, 2–80 characters after trimming", example = "maria")
            @RequestParam String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return people.search(q, page, size);
    }

    @GetMapping("/{id}/followers")
    @Operation(summary = "List a user's followers",
            description = "Newest follow first, with follow ID as tiebreaker. Page is zero-based; size is 1–50.")
    @ApiResponse(responseCode = "200", description = "Page of public profiles")
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<UserPublic> followers(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return people.connections(id, FOLLOWERS, page, size);
    }

    @GetMapping("/{id}/following")
    @Operation(summary = "List the people a user follows",
            description = "Newest follow first, with follow ID as tiebreaker. Page is zero-based; size is 1–50.")
    @ApiResponse(responseCode = "200", description = "Page of public profiles")
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<UserPublic> following(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return people.connections(id, FOLLOWING, page, size);
    }

    @PostMapping("/{id}/follow")
    @Operation(summary = "Follow a user", description = "Requires CSRF. Repeating the request preserves the original follow time.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "200", description = "User is followed, with current follower count")
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Cannot follow yourself",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public FollowState follow(Principal principal, @PathVariable long id) {
        return people.follow(Long.parseLong(principal.getName()), id);
    }

    @DeleteMapping("/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unfollow a user", description = "Requires CSRF. A missing follow of an existing user is a successful no-op.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "204", description = "User is no longer followed", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF rejected",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Cannot unfollow yourself",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public void unfollow(Principal principal, @PathVariable long id) {
        people.unfollow(Long.parseLong(principal.getName()), id);
    }
}
