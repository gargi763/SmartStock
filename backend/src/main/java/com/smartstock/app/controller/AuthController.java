package com.smartstock.app.controller;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.AuthRequest;
import com.smartstock.app.dto.LoginRequest;
import com.smartstock.app.repository.UserRepository;
import com.smartstock.app.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {
        try {
            User user = User.builder()
                    .username(request.username())
                    .email(request.email())
                    .password(passwordEncoder.encode(request.password()))
                    .build();
            user.getRoles().add(Role.USER);

            User saved = userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "username", saved.getUsername(),
                    "email", saved.getEmail(),
                    "message", "Registration successful"
            ));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Username or email is already registered"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
