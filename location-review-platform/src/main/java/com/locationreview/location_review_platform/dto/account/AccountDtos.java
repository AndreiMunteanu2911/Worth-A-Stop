package com.locationreview.location_review_platform.dto.account;

import com.locationreview.location_review_platform.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Locale;

public final class AccountDtos {
    private AccountDtos() { }
    public record RegisterRequest(
        @Schema(description = "Trimmed and lowercased; 3–30 ASCII letters, digits or underscores", example = "reader_1", requiredMode = Schema.RequiredMode.REQUIRED) String username,
        @Schema(description = "Trimmed and lowercased; maximum 254 characters", example = "reader@example.com", requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "12–72 UTF-8 bytes", format = "password", requiredMode = Schema.RequiredMode.REQUIRED) String password,
        @Schema(description = "Nonblank display name, at most 80 characters", requiredMode = Schema.RequiredMode.REQUIRED) String displayName) { }
    @Schema(description = "Supply at least one field. Omitted fields are unchanged.")
    public record ProfilePatchRequest(
        @Schema(description = "Trimmed and lowercased; 3–30 ASCII letters, digits or underscores") String username,
        @Schema(description = "Trimmed and lowercased; maximum 254 characters") String email,
        @Schema(description = "Nonblank display name, at most 80 characters") String displayName,
        @Schema(description = "Biography, at most 500 characters") String biography) { }
    public record UserPublic(Long id, String username, String displayName, String biography,
                             String avatarPlaceholder, long followerCount, long followingCount) {
        public static UserPublic from(User user, long followers, long following) {
            String name = user.getDisplayName();
            String initial = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            return new UserPublic(user.getId(), user.getUsername(), name, user.getBiography(), initial, followers, following);
        }
    }
    public record UserSelf(Long id, String username, String displayName, String biography,
                           String avatarPlaceholder, long followerCount, long followingCount,
                           String email, Instant createdAt) { }
}
