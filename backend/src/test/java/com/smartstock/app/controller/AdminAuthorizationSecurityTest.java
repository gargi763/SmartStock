package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuthorizationTestController.class)
@Import(SecurityConfig.class)
class AdminAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminRoute_rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminRoute_rejectsAuthenticatedOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRoute_allowsAuthorizedAdministrators() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin access granted"));
    }

    @Test
    void h2Console_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void h2Console_rejectsOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isForbidden());
    }

}

@RestController
@RequestMapping("/api/v1/admin")
class AdminAuthorizationTestController {
    @GetMapping("/test")
    public String testAccess() {
        return "admin access granted";
    }
}
