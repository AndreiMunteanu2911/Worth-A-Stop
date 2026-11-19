package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.dto.account.AccountDtos.*;
import com.locationreview.location_review_platform.service.AccountService;
import com.locationreview.location_review_platform.controller.web.SessionLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountApiController {
    private final AccountService accounts;
    public AccountApiController(AccountService accounts) { this.accounts = accounts; }
    @PostMapping("/auth/register")
    @Operation(summary = "Register and start a session", description = "Requires a CSRF token. Username and email are normalized.", responses = {@ApiResponse(responseCode="201", description="Account created"), @ApiResponse(responseCode="400", description="Invalid fields"), @ApiResponse(responseCode="409", description="Username or email already in use")})
    public ResponseEntity<UserSelf> register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        UserSelf user = accounts.register(request); SessionLogin.start(user.id(), servletRequest, response);
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(user);
    }
    @GetMapping("/users/me")
    @Operation(summary="Read the signed-in account", security=@SecurityRequirement(name="sessionCookie"), responses={@ApiResponse(responseCode="200", description="Account"), @ApiResponse(responseCode="401", description="Authentication required")})
    public UserSelf me(Principal principal) { return accounts.self(Long.parseLong(principal.getName())); }
    @PatchMapping("/users/me")
    @Operation(summary="Update the signed-in account", description="Supply at least one field and a CSRF token.", security=@SecurityRequirement(name="sessionCookie"), responses={@ApiResponse(responseCode="200", description="Updated"), @ApiResponse(responseCode="400", description="Invalid fields"), @ApiResponse(responseCode="401", description="Authentication required"), @ApiResponse(responseCode="403", description="CSRF rejected"), @ApiResponse(responseCode="409", description="Username or email already in use")})
    public UserSelf update(Principal principal, @RequestBody ProfilePatchRequest request) { return accounts.update(Long.parseLong(principal.getName()), request); }
    @GetMapping("/users/{id}")
    @Operation(summary="Read a public profile", responses={@ApiResponse(responseCode="200", description="Public profile"), @ApiResponse(responseCode="404", description="User not found")})
    public UserPublic profile(@PathVariable long id) { return accounts.publicProfile(id); }
}
