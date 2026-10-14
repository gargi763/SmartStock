package com.smartstock.app.controller;

import com.smartstock.app.config.GlobalExceptionHandler;
import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.dto.AdminStockRequest;
import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.dto.UpdateStockMetadataRequest;
import com.smartstock.app.exception.DuplicateStockException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.StockDeletionNotAllowedException;
import com.smartstock.app.service.AdminStockService;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStockController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminStockControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminStockService adminStockService;

    @Test
    void adminStockEndpoints_rejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stocks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stocks/AAPL")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"companyName\":\"Microsoft\",\"sector\":\"Technology\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Apple\",\"sector\":\"Technology\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/admin/stocks/AAPL")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminStockEndpoints_rejectOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stocks")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stocks/AAPL")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"companyName\":\"Microsoft\",\"sector\":\"Technology\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Apple\",\"sector\":\"Technology\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/stocks/AAPL")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanListGetCreateUpdateAndDeleteStocks() throws Exception {
        StockResponse response = new StockResponse(
                1L, "MSFT", "Microsoft Corporation", BigDecimal.ZERO,
                null, null, "unavailable", "Technology");
        when(adminStockService.getStocks()).thenReturn(List.of(response));
        when(adminStockService.getStock("MSFT")).thenReturn(response);
        when(adminStockService.createStock(new AdminStockRequest("MSFT", "Microsoft Corporation", "Technology")))
                .thenReturn(response);
        when(adminStockService.updateMetadata("MSFT",
                new UpdateStockMetadataRequest("Microsoft", "Technology"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("MSFT"));
        mockMvc.perform(get("/api/v1/admin/stocks/MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketSource").value("unavailable"));
        mockMvc.perform(post("/api/v1/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"companyName\":\"Microsoft Corporation\",\"sector\":\"Technology\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentPrice").value(0))
                .andExpect(jsonPath("$.marketSource").value("unavailable"));
        mockMvc.perform(put("/api/v1/admin/stocks/MSFT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Microsoft\",\"sector\":\"Technology\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/stocks/MSFT"))
                .andExpect(status().isNoContent());

        verify(adminStockService).deleteStock("MSFT");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateInvalidMissingAndReferencedStockErrorsAreReturned() throws Exception {
        when(adminStockService.createStock(new AdminStockRequest("AAPL", "Apple", "Technology")))
                .thenThrow(new DuplicateStockException("AAPL"));
        when(adminStockService.getStock("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Stock not found: UNKNOWN"));
        doThrow(new StockDeletionNotAllowedException("AAPL"))
                .when(adminStockService).deleteStock("AAPL");

        mockMvc.perform(post("/api/v1/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"companyName\":\"Apple\",\"sector\":\"Technology\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admin/stocks/UNKNOWN"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/stocks/AAPL"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"BAD SYMBOL\",\"companyName\":\"\",\"sector\":\"Technology\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Apple\"}"))
                .andExpect(status().isBadRequest());
    }
}
