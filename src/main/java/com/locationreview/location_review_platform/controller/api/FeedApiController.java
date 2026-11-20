package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.response.PageResponse;
import com.locationreview.location_review_platform.service.FeedService;
import com.locationreview.location_review_platform.service.FeedService.FeedItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedApiController {
    private final FeedService feed;

    public FeedApiController(FeedService feed) {
        this.feed = feed;
    }

    @GetMapping("/api/v1/feed")
    @Operation(summary = "Read your activity feed",
            description = "Reviews and currently public collections from followed accounts, ordered by publication time descending, kind ascending, and target ID descending.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "200", description = "Page of public activity, including empty pages")
    @ApiResponse(responseCode = "400", description = "Invalid pagination",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<FeedItem> read(Principal principal,
            @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 50", example = "20") @RequestParam(defaultValue = "20") int size) {
        return feed.read(Long.parseLong(principal.getName()), page, size);
    }
}
