package com.smartstock.app.service;

import com.smartstock.app.domain.User;
import com.smartstock.app.domain.Role;
import com.smartstock.app.dto.LoginRequest;
import com.smartstock.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ResponseEntity<?> login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.username());
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }
        return ResponseEntity.ok(Map.of(
                "id", userOpt.get().getId(),
                "username", userOpt.get().getUsername(),
                "email", userOpt.get().getEmail(),
                "roles", userOpt.get().getRoles().stream().map(Role::name).sorted().toList(),
                "message", "Login successful"
        ));
    }
}
