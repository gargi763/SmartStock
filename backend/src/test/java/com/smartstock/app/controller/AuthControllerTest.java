package com.smartstock.app.controller;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.AuthRequest;
import com.smartstock.app.repository.UserRepository;
import com.smartstock.app.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void register_withBuilderDefaults_assignsUserRoleAndSavesUser() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService authService = mock(AuthService.class);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        AuthController controller = new AuthController(userRepository, passwordEncoder, authService);
        ResponseEntity<?> response = controller.register(
                new AuthRequest("new-user", "new-user@example.test", "password"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        org.mockito.ArgumentCaptor<User> savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getRoles()).containsExactly(Role.USER);
        assertThat(savedUser.getValue().getPortfolios()).isEmpty();
    }
}
