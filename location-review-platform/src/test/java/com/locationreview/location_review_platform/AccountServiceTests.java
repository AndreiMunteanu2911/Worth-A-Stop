package com.locationreview.location_review_platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.locationreview.location_review_platform.dto.account.AccountDtos.*;
import com.locationreview.location_review_platform.entity.User;
import com.locationreview.location_review_platform.repository.*;
import com.locationreview.location_review_platform.service.*;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AccountServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AccountService service = new AccountService(users, follows, encoder, Validation.buildDefaultValidatorFactory().getValidator());

    @Test void registrationNormalizesAndHashes() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        var view = service.register(new RegisterRequest(" READER_1 ", " Person@Example.COM ", "long enough password", " Reader "));
        assertThat(view.username()).isEqualTo("reader_1");
        assertThat(view.email()).isEqualTo("person@example.com");
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertThat(encoder.matches("long enough password", captor.getValue().getPasswordHash())).isTrue();
    }
    @Test void rejectsInvalidFieldsAndUtf8PasswordLength() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("!", "bad", "short", " ")))
            .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.fieldErrors()).containsKeys("username", "email", "password", "displayName"));
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid", "valid@example.com", "é".repeat(37), "Valid")))
            .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.fieldErrors()).containsKey("password"));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void rejectsDuplicatesAndEmptyPatch() {
        when(users.existsByUsernameAndIdNot("taken", 0L)).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Taken", "valid@example.com", "long enough password", "Valid")))
            .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.update(1, new ProfilePatchRequest(null, null, null, null)))
            .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    @Test void rejectsMissingProfile() {
        when(users.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publicProfile(42))
            .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
