package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.domain.Role;
import com.smartstock.app.dto.AdminUserResponse;
import com.smartstock.app.dto.UpdateUserRoleRequest;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.UserRoleChangeNotAllowedException;
import com.smartstock.app.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @Test
    void adminUserEndpoints_rejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/users/10"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/users/10/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminUserEndpoints_rejectOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users/10"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/users/10/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorCanListAndRetrieveSanitizedUserRecords() throws Exception {
        AdminUserResponse response = new AdminUserResponse(
                10L, "alice", "alice@example.test", Set.of(Role.USER));
        when(adminUserService.getUsers()).thenReturn(List.of(response));
        when(adminUserService.getUser(10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].email").value("alice@example.test"))
                .andExpect(jsonPath("$[0].roles[0]").value("USER"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/users/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorCanChangeAnotherUsersRoleAndCannotChangeOwnRole() throws Exception {
        AdminUserResponse response = new AdminUserResponse(
                10L, "alice", "alice@example.test", Set.of(Role.ADMIN));
        when(adminUserService.updateUserRole(10L, Role.ADMIN, "admin")).thenReturn(response);
        doThrow(new UserRoleChangeNotAllowedException("Administrators cannot change their own role"))
                .when(adminUserService).updateUserRole(1L, Role.USER, "admin");

        mockMvc.perform(put("/api/v1/admin/users/10/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(put("/api/v1/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Administrators cannot change their own role"));
        verify(adminUserService).updateUserRole(10L, Role.ADMIN, "admin");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidUserIdsAndRolesReturnClientErrors() throws Exception {
        when(adminUserService.getUser(999L))
                .thenThrow(new ResourceNotFoundException("User not found: 999"));
        when(adminUserService.getUser(0L))
                .thenThrow(new ResourceNotFoundException("User not found: 0"));

        mockMvc.perform(get("/api/v1/admin/users/999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/users/0"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/users/10/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROOT\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/users/10/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
