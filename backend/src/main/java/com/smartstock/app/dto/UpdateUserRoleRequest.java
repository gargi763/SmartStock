package com.smartstock.app.dto;

import com.smartstock.app.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {}
