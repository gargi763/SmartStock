package com.smartstock.app.controller;

import com.smartstock.app.dto.AdminUserResponse;
import com.smartstock.app.dto.UpdateUserRoleRequest;
import com.smartstock.app.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> getUsers() {
        return ResponseEntity.ok(adminUserService.getUsers());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUser(userId));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<AdminUserResponse> updateUserRole(@PathVariable Long userId,
                                                            @Valid @RequestBody UpdateUserRoleRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.ok(adminUserService.updateUserRole(
                userId, request.role(), authentication.getName()));
    }
}
