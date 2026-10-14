package com.smartstock.app.service;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.AdminUserResponse;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.UserRoleChangeNotAllowedException;
import com.smartstock.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, Role role, String actingUsername) {
        if (userId == null || userId <= 0) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        if (role == null) {
            throw new UserRoleChangeNotAllowedException("A valid role is required");
        }

        User user = findUser(userId);
        if (user.getUsername().equals(actingUsername)) {
            throw new UserRoleChangeNotAllowedException("Administrators cannot change their own role");
        }

        user.setRoles(new HashSet<>(Set.of(role)));
        return toResponse(userRepository.save(user));
    }

    private User findUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Set.copyOf(user.getRoles())
        );
    }
}
