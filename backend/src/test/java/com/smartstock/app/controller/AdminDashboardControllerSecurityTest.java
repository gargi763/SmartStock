package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.dto.AdminDashboardResponse;
import com.smartstock.app.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
class AdminDashboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDashboardService adminDashboardService;

    @Test
    void dashboard_rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_rejectsOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboard_returnsAggregateSummaryToAdministrators() throws Exception {
        when(adminDashboardService.getDashboard()).thenReturn(new AdminDashboardResponse(
                3, 2, 1, 4, 5, 6, 7, 4, 3, 8, 3, 1));

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegisteredUsers").value(3))
                .andExpect(jsonPath("$.usersWithUserRole").value(2))
                .andExpect(jsonPath("$.usersWithAdminRole").value(1))
                .andExpect(jsonPath("$.totalStocks").value(4))
                .andExpect(jsonPath("$.totalPortfolios").value(5))
                .andExpect(jsonPath("$.totalHoldings").value(6))
                .andExpect(jsonPath("$.totalVirtualTransactions").value(7))
                .andExpect(jsonPath("$.buyTransactions").value(4))
                .andExpect(jsonPath("$.sellTransactions").value(3))
                .andExpect(jsonPath("$.totalWatchlistEntries").value(8))
                .andExpect(jsonPath("$.stocksWithAvailablePriceSource").value(3))
                .andExpect(jsonPath("$.stocksWithUnavailablePriceSource").value(1))
                .andExpect(jsonPath("$.users[0]").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(adminDashboardService).getDashboard();
    }
}
