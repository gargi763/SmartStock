package com.smartstock.app.service;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.LoginRequest;
import com.smartstock.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void login_returnsPersistedRolesWithoutAuthenticationSecrets() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        User admin = User.builder()
                .id(7L)
                .username("admin-user")
                .email("admin@example.test")
                .password("encoded-password")
                .roles(Set.of(Role.ADMIN, Role.USER))
                .build();
        when(userRepository.findByUsername("admin-user")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        ResponseEntity<?> response = new AuthService(userRepository, passwordEncoder)
                .login(new LoginRequest("admin-user", "password"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> payload = (Map<?, ?>) response.getBody();
        assertThat(payload.get("roles")).isEqualTo(java.util.List.of("ADMIN", "USER"));
        assertThat(payload.containsKey("password")).isFalse();
        assertThat(payload.containsKey("passwordHash")).isFalse();
    }
}
