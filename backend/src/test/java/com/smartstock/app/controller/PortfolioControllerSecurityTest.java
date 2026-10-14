package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@Import(SecurityConfig.class)
class PortfolioControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    void portfolioEndpoints_rejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/portfolios").param("name", "Private"))
                .andExpect(status().isUnauthorized());

        String trade = """
                {"symbol":"AAPL","quantity":1,"price":200}
                """;
        mockMvc.perform(post("/api/v1/portfolios/42/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trade))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/portfolios/42/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trade))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void portfolioControllerUsesAuthenticatedPrincipalForPortfolioReads() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk());

        verify(portfolioService).getPortfoliosForUser("alice");
    }
}
