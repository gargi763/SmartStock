package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.dto.InvestmentJournalEntryRequest;
import com.smartstock.app.dto.InvestmentJournalEntryResponse;
import com.smartstock.app.service.InvestmentJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestmentJournalController.class)
@Import(SecurityConfig.class)
class InvestmentJournalControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestmentJournalService journalService;

    @Test
    void allJournalEndpoints_rejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/investment-journal")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/investment-journal/trades")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/investment-journal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEntry()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/investment-journal/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEntry()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/investment-journal/4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void entryOperationsUseAuthenticatedPrincipalAndValidateRequest() throws Exception {
        InvestmentJournalEntryResponse response = new InvestmentJournalEntryResponse(
                4L, "Plan", "Thesis", "Lesson", List.of("risk"),
                LocalDateTime.of(2026, 10, 14, 10, 0),
                LocalDateTime.of(2026, 10, 14, 10, 0),
                null, null, null, null);
        when(journalService.createEntry(eq("alice"), any(InvestmentJournalEntryRequest.class)))
                .thenReturn(response);
        when(journalService.updateEntry(eq("alice"), eq(4L), any(InvestmentJournalEntryRequest.class)))
                .thenReturn(response);
        when(journalService.getEntries("alice")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/investment-journal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Plan"));
        mockMvc.perform(post("/api/v1/investment-journal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEntry()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
        mockMvc.perform(put("/api/v1/investment-journal/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEntry()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/investment-journal/4"))
                .andExpect(status().isNoContent());

        verify(journalService).getEntries("alice");
        verify(journalService).createEntry(eq("alice"), any(InvestmentJournalEntryRequest.class));
        verify(journalService).updateEntry(eq("alice"), eq(4L), any(InvestmentJournalEntryRequest.class));
        verify(journalService).deleteEntry("alice", 4L);
    }

    @Test
    @WithMockUser(username = "alice")
    void createEntry_rejectsInvalidContent() throws Exception {
        mockMvc.perform(post("/api/v1/investment-journal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"thesis\":\"\",\"lessonsLearned\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private String validEntry() {
        return """
                {
                  "title": "Plan",
                  "thesis": "Why I made this decision",
                  "lessonsLearned": "What I learned",
                  "tags": ["risk"],
                  "transactionId": null
                }
                """;
    }
}
