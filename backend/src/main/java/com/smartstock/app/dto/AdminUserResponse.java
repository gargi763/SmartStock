package com.smartstock.app.dto;

import com.smartstock.app.domain.Role;

import java.util.Set;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        Set<Role> roles
) {}
