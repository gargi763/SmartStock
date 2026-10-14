package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.dto.WatchlistItemResponse;
import com.smartstock.app.exception.DuplicateWatchlistItemException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.service.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import(SecurityConfig.class)
class WatchlistControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistService watchlistService;

    @Test
    void watchlistEndpoints_rejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/watchlist/AAPL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void endpointsUseAuthenticatedUsernameAndReturnExpectedStatuses() throws Exception {
        WatchlistItemResponse response = new WatchlistItemResponse(
                1L, "AAPL", "Apple Inc", "Technology",
                new BigDecimal("200.00"), null, null, "demo");
        when(watchlistService.getWatchlist("alice")).thenReturn(List.of(response));
        when(watchlistService.addStock("alice", "AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
        mockMvc.perform(delete("/api/v1/watchlist/AAPL"))
                .andExpect(status().isNoContent());

        verify(watchlistService).getWatchlist("alice");
        verify(watchlistService).addStock("alice", "AAPL");
        verify(watchlistService).removeStock("alice", "AAPL");
    }

    @Test
    @WithMockUser(username = "alice")
    void duplicateAndMissingEntriesReturnClearHttpErrors() throws Exception {
        when(watchlistService.addStock(eq("alice"), eq("AAPL")))
                .thenThrow(new DuplicateWatchlistItemException("AAPL"));
        doThrow(new ResourceNotFoundException("Stock not found: UNKNOWN"))
                .when(watchlistService).removeStock("alice", "UNKNOWN");

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stock is already in the watchlist: AAPL"));
        mockMvc.perform(delete("/api/v1/watchlist/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Stock not found: UNKNOWN"));
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"bad/symbol\"}"))
                .andExpect(status().isBadRequest());
    }
}
