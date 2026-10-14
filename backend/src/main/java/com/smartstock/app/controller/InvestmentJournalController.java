package com.smartstock.app.controller;

import com.smartstock.app.dto.InvestmentJournalEntryRequest;
import com.smartstock.app.dto.InvestmentJournalEntryResponse;
import com.smartstock.app.dto.InvestmentJournalTradeResponse;
import com.smartstock.app.service.InvestmentJournalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/investment-journal")
public class InvestmentJournalController {

    private final InvestmentJournalService journalService;

    public InvestmentJournalController(InvestmentJournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    public ResponseEntity<List<InvestmentJournalEntryResponse>> getEntries(Authentication authentication) {
        return ResponseEntity.ok(journalService.getEntries(authentication.getName()));
    }

    @GetMapping("/trades")
    public ResponseEntity<List<InvestmentJournalTradeResponse>> getCompletedTrades(Authentication authentication) {
        return ResponseEntity.ok(journalService.getCompletedTrades(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<InvestmentJournalEntryResponse> createEntry(
            @Valid @RequestBody InvestmentJournalEntryRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(journalService.createEntry(authentication.getName(), request));
    }

    @PutMapping("/{entryId}")
    public ResponseEntity<InvestmentJournalEntryResponse> updateEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody InvestmentJournalEntryRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(journalService.updateEntry(authentication.getName(), entryId, request));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long entryId, Authentication authentication) {
        journalService.deleteEntry(authentication.getName(), entryId);
        return ResponseEntity.noContent().build();
    }
}
