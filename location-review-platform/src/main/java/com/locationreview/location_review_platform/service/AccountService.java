package com.locationreview.location_review_platform.service;

import com.locationreview.location_review_platform.dto.account.AccountDtos.*;
import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.FollowRepository;
import com.locationreview.location_review_platform.repository.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final FollowRepository follows;
    private final PasswordEncoder encoder;
    private final Validator validator;
    public AccountService(UserRepository users, FollowRepository follows, PasswordEncoder encoder, Validator validator) {
        this.users = users; this.follows = follows; this.encoder = encoder; this.validator = validator;
    }
    private record EmailValue(@Email String value) { }
    public static String normalize(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    private static String trim(String value) { return value == null ? null : value.strip(); }
    private void validate(String username, String email, String displayName, String biography, String password, boolean registration) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (username == null || !username.matches("[a-z0-9_]{3,30}")) errors.put("username", "Use 3–30 letters, digits or underscores.");
        if (email == null || email.isEmpty() || !email.contains("@") || email.length() > 254 || !validator.validate(new EmailValue(email)).isEmpty()) errors.put("email", "Enter a valid email address (up to 254 characters).");
        if (displayName == null || displayName.isEmpty() || displayName.codePointCount(0, displayName.length()) > 80) errors.put("displayName", "Enter a display name of 1–80 characters.");
        if (biography == null || biography.codePointCount(0, biography.length()) > 500) errors.put("biography", "Biography must be at most 500 characters.");
        if (registration && (password == null || password.getBytes(StandardCharsets.UTF_8).length < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)) errors.put("password", "Password must be 12–72 UTF-8 bytes.");
        if (!errors.isEmpty()) throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields", "Please correct the indicated fields.", errors);
    }
    private void unique(String username, String email, Long id) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (users.existsByUsernameAndIdNot(username, id)) errors.put("username", "Username is already in use.");
        if (users.existsByEmailAndIdNot(email, id)) errors.put("email", "Email is already in use.");
        if (!errors.isEmpty()) throw new AccountException(HttpStatus.CONFLICT, "account_conflict", "Username or email is already in use.", errors);
    }
    @Transactional
    public UserSelf register(RegisterRequest request) {
        String username = normalize(request.username()), email = normalize(request.email()), displayName = trim(request.displayName());
        validate(username, email, displayName, "", request.password(), true);
        unique(username, email, 0L);
        User user = new User();
        user.setUsername(username); user.setEmail(email); user.setDisplayName(displayName); user.setBiography("");
        user.setPasswordHash(encoder.encode(request.password()));
        Instant now = Instant.now(); user.setCreatedAt(now); user.setUpdatedAt(now);
        try { return self(users.saveAndFlush(user)); }
        catch (DataIntegrityViolationException ex) { throw conflict(); }
    }
    @Transactional
    public UserSelf update(long id, ProfilePatchRequest request) {
        if (request.username() == null && request.email() == null && request.displayName() == null && request.biography() == null)
            throw new AccountException(HttpStatus.BAD_REQUEST, "invalid_fields", "At least one field is required.", Map.of());
        User user = get(id);
        String username = request.username() == null ? user.getUsername() : normalize(request.username());
        String email = request.email() == null ? user.getEmail() : normalize(request.email());
        String name = request.displayName() == null ? user.getDisplayName() : trim(request.displayName());
        String biography = request.biography() == null ? user.getBiography() : trim(request.biography());
        validate(username, email, name, biography, null, false); unique(username, email, id);
        user.setUsername(username); user.setEmail(email); user.setDisplayName(name); user.setBiography(biography); user.setUpdatedAt(Instant.now());
        try { return self(users.saveAndFlush(user)); }
        catch (DataIntegrityViolationException ex) { throw conflict(); }
    }
    private AccountException conflict() { return new AccountException(HttpStatus.CONFLICT, "account_conflict", "Username or email is already in use.", Map.of()); }
    @Transactional(readOnly = true)
    public UserSelf self(long id) { return self(get(id)); }
    @Transactional(readOnly = true)
    public UserPublic publicProfile(long id) { return publicView(get(id)); }
    private User get(long id) { return users.findById(id).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "user_not_found", "User not found.", Map.of())); }
    private UserPublic publicView(User user) {
        return UserPublic.from(user, follows.countByFollowedId(user.getId()), follows.countByFollowerId(user.getId()));
    }
    private UserSelf self(User user) {
        UserPublic view = publicView(user);
        return new UserSelf(view.id(), view.username(), view.displayName(), view.biography(), view.avatarPlaceholder(), view.followerCount(), view.followingCount(), user.getEmail(), user.getCreatedAt());
    }
}
